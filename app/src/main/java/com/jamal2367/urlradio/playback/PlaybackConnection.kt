/*
 * PlaybackConnection.kt
 * Owns the MediaController connection to PlayerService and exposes playback as a StateFlow
 *
 * This replaces the controller handling that used to live in PlayerFragment, including the
 * Handler-driven 500 ms sleep timer poll. The poll now runs as a coroutine that is tied to
 * the connection, so it cannot outlive the screen.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.PlayerService
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.CollectionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** How often the sleep timer remaining time is polled from the service. */
private const val SLEEP_TIMER_POLL_INTERVAL_MS = 500L

/*
 * androidx.annotation.OptIn (not Kotlin's) is what the UnsafeOptInUsageError lint check
 * looks for. Marking the class @UnstableApi instead would propagate the requirement to
 * every caller, which is not what an app wants.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackConnection(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pollJob: Job? = null

    private val controller: MediaController?
        get() = controllerFuture?.takeIf { it.isDone && !it.isCancelled }?.get()

    private val listener = object : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _state.update { it.copy(stationUuid = mediaItem?.mediaId.orEmpty()) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update {
                it.copy(
                    isPlaying = isPlaying,
                    // buffering means: playback was requested but is not running yet
                    isBuffering = !isPlaying && controller?.playWhenReady == true,
                )
            }
            refreshMetadataHistory()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _state.update {
                it.copy(isBuffering = playWhenReady && controller?.isPlaying != true)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorEvent = System.currentTimeMillis(),
                )
            }
        }
    }

    /* Connects to PlayerService. Safe to call repeatedly. */
    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({ onControllerReady() }, MoreExecutors.directExecutor())
    }

    /* Releases the controller. Safe to call repeatedly. */
    fun release() {
        pollJob?.cancel()
        pollJob = null
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _state.update { it.copy(isConnected = false) }
    }

    private fun onControllerReady() {
        val controller = this.controller ?: return
        controller.addListener(listener)
        _state.update {
            it.copy(
                isConnected = true,
                stationUuid = controller.currentMediaItem?.mediaId.orEmpty(),
                isPlaying = controller.isPlaying,
                isBuffering = controller.playWhenReady && !controller.isPlaying,
            )
        }
        refreshMetadataHistory()
        startPolling()
    }

    /*
     * Polls the sleep timer while there is something worth polling for. The old
     * implementation used a Handler that ran every 500 ms forever, including while the
     * app was idle, and was never removed on destroy.
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val current = _state.value
                if (current.isPlaying || current.sleepTimerRemaining > 0L) {
                    refreshSleepTimer()
                } else if (current.sleepTimerRemaining != 0L) {
                    _state.update { it.copy(sleepTimerRemaining = 0L) }
                }
                delay(SLEEP_TIMER_POLL_INTERVAL_MS)
            }
        }
    }

    private fun refreshSleepTimer() {
        val controller = this.controller ?: return
        val future = controller.sendCustomCommand(
            SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY),
            Bundle.EMPTY,
        )
        future.addListener({
            val remaining = runCatching {
                future.get().extras.getLong(Keys.EXTRA_SLEEP_TIMER_REMAINING)
            }.getOrDefault(0L)
            _state.update { it.copy(sleepTimerRemaining = remaining) }
        }, MoreExecutors.directExecutor())
    }

    fun refreshMetadataHistory() {
        val controller = this.controller ?: return
        val future = controller.sendCustomCommand(
            SessionCommand(Keys.CMD_REQUEST_METADATA_HISTORY, Bundle.EMPTY),
            Bundle.EMPTY,
        )
        future.addListener({
            val history = runCatching {
                future.get().extras.getStringArrayList(Keys.EXTRA_METADATA_HISTORY)
            }.getOrNull().orEmpty()
            if (history.isNotEmpty()) {
                _state.update { it.copy(metadataHistory = history.toList()) }
            }
        }, MoreExecutors.directExecutor())
    }

    /* Starts playback of a station, replacing whatever was playing. */
    fun play(station: Station) {
        val controller = this.controller ?: return
        if (controller.isPlaying) controller.pause()
        controller.setMediaItem(CollectionHelper.buildMediaItem(context, station))
        controller.prepare()
        controller.play()
    }

    fun pause() {
        controller?.pause()
    }

    /*
     * Tapping the station that is currently playing pauses it; tapping any other station
     * starts that one. Mirrors the behaviour of the old onPlayButtonTapped.
     */
    fun togglePlayPause(station: Station) {
        val controller = this.controller ?: return
        if (controller.isPlaying && station.uuid == _state.value.stationUuid) {
            controller.pause()
        } else {
            play(station)
        }
    }

    /* Plays a stream address that is not part of the collection. */
    fun playStreamDirectly(streamUri: String) {
        val controller = this.controller ?: return
        val args = Bundle().apply { putString(Keys.KEY_STREAM_URI, streamUri) }
        controller.sendCustomCommand(SessionCommand(Keys.CMD_PLAY_STREAM, Bundle.EMPTY), args)
    }

    fun startSleepTimer(durationMillis: Long) {
        val controller = this.controller ?: return
        val args = Bundle().apply { putLong(Keys.SLEEP_TIMER_DURATION, durationMillis) }
        controller.sendCustomCommand(SessionCommand(Keys.CMD_START_SLEEP_TIMER, args), args)
        refreshSleepTimer()
    }

    fun cancelSleepTimer() {
        val controller = this.controller ?: return
        controller.sendCustomCommand(
            SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY),
            Bundle.EMPTY,
        )
        _state.update { it.copy(sleepTimerRemaining = 0L) }
    }

    /* Clears the one-shot error flag after the UI has shown it. */
    fun consumeError() {
        _state.update { it.copy(errorEvent = null) }
    }
}

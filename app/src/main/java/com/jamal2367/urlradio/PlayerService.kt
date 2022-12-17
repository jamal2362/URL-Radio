/*
 * PlayerService.kt
 * Implements the PlayerService class
 * PlayerService is URLRadio's foreground service that plays radio station audio
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.*
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import com.jamal2367.urlradio.core.Collection
import com.jamal2367.urlradio.helpers.*
import java.util.*


/*
 * PlayerService class
 */
@UnstableApi
class PlayerService: MediaLibraryService() {

    /* Define log tag */
    private val TAG: String = PlayerService::class.java.simpleName

    /* Main class variables */
    private lateinit var player: Player
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var sleepTimer: CountDownTimer
    var sleepTimerTimeRemaining: Long = 0L
    private val librarySessionCallback = CustomMediaLibrarySessionCallback()
    private var collection: Collection = Collection()
    private lateinit var metadataHistory: MutableList<String>
    private lateinit var modificationDate: Date
    private var playbackRestartCounter: Int = 0
    private var playbackActive = false // todo remove


    /* Overrides onCreate from Service */
    override fun onCreate() {
        super.onCreate()
        // load collection
        collection = FileHelper.readCollection(this)
        // create and register collection changed receiver
        LocalBroadcastManager.getInstance(application).registerReceiver(collectionChangedReceiver, IntentFilter(Keys.ACTION_COLLECTION_CHANGED))
        // initialize player and session
        initializePlayer()
        initializeSession()
        setMediaNotificationProvider(CustomNotificationProvider())
        // fetch the metadata history
        metadataHistory = PreferencesHelper.loadMetadataHistory()
    }


    /* Overrides onDestroy from Service */
    override fun onDestroy() {
        // player.removeAnalyticsListener(analyticsListener)
        player.removeListener(playerListener)
        player.release()
        mediaLibrarySession.release()
        super.onDestroy()
    }


    /* Overrides onGetSession from MediaSessionService */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }


    /* Initializes the ExoPlayer */
    private fun initializePlayer() {
        val exoPlayer: ExoPlayer = ExoPlayer.Builder(this).apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
        }.build()
        exoPlayer.addAnalyticsListener(analyticsListener)
        exoPlayer.addListener(playerListener)

        // manually add seek to next and seek to previous since headphones issue them and they are translated to next and previous station
        player = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon().add(COMMAND_SEEK_TO_NEXT).add(COMMAND_SEEK_TO_PREVIOUS).build()
            }
        }
    }


    /* Initializes the MediaSession */
    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntent(intent)
            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, librarySessionCallback).apply {
            setSessionActivity(pendingIntent)
        }.build()
    }


    /* Starts sleep timer / adds default duration to running sleeptimer */
    private fun startSleepTimer() {
        // stop running timer
        if (sleepTimerTimeRemaining > 0L && this::sleepTimer.isInitialized) {
            sleepTimer.cancel()
        }
        // initialize timer
        sleepTimer = object: CountDownTimer(Keys.SLEEP_TIMER_DURATION + sleepTimerTimeRemaining, Keys.SLEEP_TIMER_INTERVAL) {
            override fun onFinish() {
                Log.v(TAG, "Sleep timer finished. Sweet dreams.")
                sleepTimerTimeRemaining = 0L
                player.pause() // todo may use player.stop() here
            }
            override fun onTick(millisUntilFinished: Long) {
                sleepTimerTimeRemaining = millisUntilFinished
            }
        }
        // start timer
        sleepTimer.start()
        // store timer state
        PreferencesHelper.saveSleepTimerRunning(isRunning = true)
    }


    /* Cancels sleep timer */
    private fun cancelSleepTimer() {
        if (this::sleepTimer.isInitialized && sleepTimerTimeRemaining > 0L) {
            sleepTimerTimeRemaining = 0L
            sleepTimer.cancel()
        }
        // store timer state
        PreferencesHelper.saveSleepTimerRunning(isRunning = false)
    }

    /* Updates metadata */
    private fun updateMetadata(metadata: String = String()) {
        // get metadata string
        val metadataString: String = metadata.ifEmpty {
            player.currentMediaItem?.mediaMetadata?.artist.toString()
        }
        // remove duplicates
        if (metadataHistory.contains(metadataString)) {
            metadataHistory.removeIf { it == metadataString }
        }
        // append metadata to metadata history
        metadataHistory.add(metadataString)
        // trim metadata list
        if (metadataHistory.size > Keys.DEFAULT_SIZE_OF_METADATA_HISTORY) {
            metadataHistory.removeAt(0)
        }
        // update notification
        // TODO implement
        // this will hide the NotificationUtil.setNotification(applicationContext, Keys.NOW_PLAYING_NOTIFICATION_ID, null)
        // save history
        PreferencesHelper.saveMetadataHistory(metadataHistory)
    }


    /* Gets the most current metadata string */
    private fun getCurrentMetadata(): String {
        val metadataString: String = if (metadataHistory.isEmpty()) {
            player.currentMediaItem?.mediaMetadata?.title.toString()
        } else {
            metadataHistory.last()
        }
        return metadataString
    }



    /* Reads collection of stations from storage using GSON */
    private fun loadCollection(context: Context) {
        Log.v(TAG, "Loading collection of stations from storage")
        CoroutineScope(Main).launch {
            // load collection on background thread
            val deferred: Deferred<Collection> = async(Dispatchers.Default) { FileHelper.readCollectionSuspended(context) }
            // wait for result and update collection
            collection = deferred.await()
//            // special case: trigger metadata view update for stations that have no metadata
//            if (player.isPlaying && station.name == getCurrentMetadata()) {
//                station = CollectionHelper.getStation(collection, station.uuid)
//                updateMetadata(null)
//            }
        }
    }


    /*
     * Custom MediaSession Callback that handles player commands
     */
    private inner class CustomMediaLibrarySessionCallback: MediaLibrarySession.Callback {

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<List<MediaItem>> {
            val updatedMediaItems: List<MediaItem> =
                mediaItems.map { mediaItem -> CollectionHelper.getItem(collection, mediaItem.mediaId)
//                    if (mediaItem.requestMetadata.searchQuery != null)
//                        getMediaItemFromSearchQuery(mediaItem.requestMetadata.searchQuery!!)
//                    else MediaItemTree.getItem(mediaItem.mediaId) ?: mediaItem
                }
            return Futures.immediateFuture(updatedMediaItems)


//            val updatedMediaItems = mediaItems.map { mediaItem ->
//                mediaItem.buildUpon().apply {
//                    setUri(mediaItem.requestMetadata.mediaUri)
//                }.build()
//            }
//            return Futures.immediateFuture(updatedMediaItems)
        }


        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            // add custom commands
            val connectionResult: MediaSession.ConnectionResult  = super.onConnect(session, controller)
            val builder: SessionCommands.Builder = connectionResult.availableSessionCommands.buildUpon()
            builder.add(SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_REQUEST_METADATA_HISTORY, Bundle.EMPTY))
            return MediaSession.ConnectionResult.accept(builder.build(), connectionResult.availablePlayerCommands)
        }

        override fun onSubscribe(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,  parentId: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            val children: List<MediaItem> = CollectionHelper.getChildren(collection)
            session.notifyChildrenChanged(browser, parentId, children.size, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children: List<MediaItem> = CollectionHelper.getChildren(collection)
            return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(CollectionHelper.getRootItem(), params))
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
            val item: MediaItem = CollectionHelper.getItem(collection, mediaId)
            return Futures.immediateFuture(LibraryResult.ofItem(item, /* params= */ null))
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                Keys.CMD_START_SLEEP_TIMER -> {
                    startSleepTimer()
                }
                Keys.CMD_CANCEL_SLEEP_TIMER -> {
                    cancelSleepTimer()
                }
                Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING -> {
                    val resultBundle = Bundle()
                    resultBundle.putLong(Keys.EXTRA_SLEEP_TIMER_REMAINING, sleepTimerTimeRemaining)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                }
                Keys.CMD_REQUEST_METADATA_HISTORY -> {
                    val resultBundle = Bundle()
                    resultBundle.putStringArrayList(Keys.EXTRA_METADATA_HISTORY, ArrayList(metadataHistory))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
            // playerCommand = one of COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_SEEK_TO_DEFAULT_POSITION, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD, COMMAND_SET_SPEED_AND_PITCH, COMMAND_SET_SHUFFLE_MODE, COMMAND_SET_REPEAT_MODE, COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_MEDIA_ITEMS_METADATA, COMMAND_SET_MEDIA_ITEMS_METADATA, COMMAND_CHANGE_MEDIA_ITEMS, COMMAND_GET_AUDIO_ATTRIBUTES, COMMAND_GET_VOLUME, COMMAND_GET_DEVICE_VOLUME, COMMAND_SET_VOLUME, COMMAND_SET_DEVICE_VOLUME, COMMAND_ADJUST_DEVICE_VOLUME, COMMAND_SET_VIDEO_SURFACE, COMMAND_GET_TEXT, COMMAND_SET_TRACK_SELECTION_PARAMETERS or COMMAND_GET_TRACK_INFOS. */
            // emulate headphone buttons
            // start/pause: adb shell input keyevent 85
            // next: adb shell input keyevent 87
            // prev: adb shell input keyevent 88
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT ->  {
                    // todo implememt
                    player.addMediaItem(CollectionHelper.getNextMediaItem(collection, player.currentMediaItem?.mediaId ?: String()))
                    player.prepare()
                    player.play()
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS ->  {
                    // todo implememt
                    player.addMediaItem(CollectionHelper.getPreviousMediaItem(collection, player.currentMediaItem?.mediaId ?: String()))
                    player.prepare()
                    player.play()
                    return SessionResult.RESULT_SUCCESS
                }
//                Player.COMMAND_PLAY_PAUSE -> {
//                    // override pause with stop, to prevent unnecessary buffering
//                    if (player.isPlaying) {
//                        player.stop()
//                        return SessionResult.RESULT_INFO_SKIPPED
//                    } else {
//                       return super.onPlayerCommandRequest(session, controller, playerCommand)
//                    }
//                }
                else -> {
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            }
        }
    }
    /*
     * End of inner class
     */



    /*
     * Custom NotificationProvider that sets tailored Notification
     */
    private inner class CustomNotificationProvider: MediaNotification.Provider {

        override fun createNotification(session: MediaSession, customLayout: ImmutableList<CommandButton>, actionFactory: MediaNotification.ActionFactory,  onNotificationChangedCallback: MediaNotification.Provider.Callback): MediaNotification {
            return MediaNotification(Keys.NOW_PLAYING_NOTIFICATION_ID, NotificationHelper(this@PlayerService).getNotification(session, actionFactory))
        }

        override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean {
            TODO("Not yet implemented")
        }
    }
    /*
     * End of inner class
     */

    /*
     * Player.Listener: Called when one or more player states changed.
     */
    private var playerListener: Player.Listener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            // store state of playback
            val currentMediaId: String = player.currentMediaItem?.mediaId ?: String()
            playbackActive = isPlaying
            PreferencesHelper.saveIsPlaying(isPlaying)
            PreferencesHelper.saveCurrentStationId(currentMediaId)
            // reset restart counter
            //playbackRestartCounter = 0
            // save collection and player state

            collection = CollectionHelper.savePlaybackState(this@PlayerService, collection, currentMediaId, isPlaying)
            //updatePlayerState(station, playbackState)

            if (isPlaying) {
                // playback is active
            } else {
                // cancel sleep timer
                cancelSleepTimer()
                // reset metadata
                updateMetadata()

                // playback is not active
                // Not playing because playback is paused, ended, suppressed, or the player
                // is buffering, stopped or failed. Check player.getPlayWhenReady,
                // player.getPlaybackState, player.getPlaybackSuppressionReason and
                // player.getPlaybackError for details.
                when (player.playbackState) {
                    // player is able to immediately play from its current position
                    Player.STATE_READY -> {
                        // todo
                    }
                    // buffering - data needs to be loaded
                    Player.STATE_BUFFERING -> {
                        // todo
                    }
                    // player finished playing all media
                    Player.STATE_ENDED -> {
                        // todo
                    }
                    // initial state or player is stopped or playback failed
                    Player.STATE_IDLE -> {
                        // todo
                    }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (!playWhenReady) {
                when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> {
                        // playback reached end: stop / end playback
                    }
                    else -> {
                        // playback has been paused by user or OS: update media session and save state
                        // PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY or
                        // PLAY_WHEN_READY_CHANGE_REASON_REMOTE
                        // handlePlaybackChange(PlaybackStateCompat.STATE_PAUSED)
                    }
                }
            }
        }

        override fun onMetadata(metadata: Metadata) {
            super.onMetadata(metadata)
            updateMetadata(AudioHelper.getMetadataString(metadata))
        }

    }
    /*
     * End of declaration
     */


    /*
     * Custom that handles Keys.ACTION_COLLECTION_CHANGED
     */
    private val collectionChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.hasExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE)) {
                val date = Date(intent.getLongExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE, 0L))

                if (date.after(collection.modificationDate)) {
                    Log.v(TAG, "PlayerService - reload collection after broadcast received.")
                    loadCollection(context)
                }
            }
        }
    }
    /*
     * End of declaration
     */


    /*
     * Custom AnalyticsListener that enables AudioFX equalizer integration
     */
    private val analyticsListener = object: AnalyticsListener {
        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            super.onAudioSessionIdChanged(eventTime, audioSessionId)
            // integrate with system equalizer (AudioFX)
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            sendBroadcast(intent)
        }
    }
    /*
     * End of declaration
     */
}

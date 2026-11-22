/*
 * PlaybackUiState.kt
 * The observable state of playback, as the UI needs it
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.playback

import androidx.compose.runtime.Immutable

/**
 * Everything the UI needs to know about playback.
 *
 * Replaces the old mix of [com.jamal2367.urlradio.ui.PlayerState], direct
 * MediaController polling and per-view mutation in LayoutHolder.
 */
@Immutable
data class PlaybackUiState(
    /** Whether the MediaController has finished connecting to PlayerService. */
    val isConnected: Boolean = false,
    /** UUID of the station the player currently holds, or empty. */
    val stationUuid: String = "",
    val isPlaying: Boolean = false,
    /** True while playback has been requested but has not started yet. */
    val isBuffering: Boolean = false,
    /** Newest last. Empty until the service has reported any metadata. */
    val metadataHistory: List<String> = emptyList(),
    /** Milliseconds left on the sleep timer, 0 when no timer is running. */
    val sleepTimerRemaining: Long = 0L,
    /** Set when playback failed; cleared once the UI has shown it. */
    val errorEvent: Long? = null,
    /** True while the playing stream is being recorded. */
    val isRecording: Boolean = false,
    /** Milliseconds the running recording has been going for, 0 when not recording. */
    val recordingElapsed: Long = 0L,
    /** Milliseconds left on the recording timer, 0 when the recording runs without one. */
    val recordingRemaining: Long = 0L,
    /** Set when a recording was saved or failed; cleared once the UI has shown it. */
    val recordingEvent: RecordingEvent? = null,
) {

    /** The newest metadata line, falling back to an empty string. */
    val currentMetadata: String get() = metadataHistory.lastOrNull().orEmpty().sanitizedMetadata()
}

/** Something the UI should tell the user about a recording. */
@Immutable
sealed interface RecordingEvent {
    /** A recording was saved under [fileName]. */
    data class Saved(val fileName: String) : RecordingEvent
    /** A recording could not be made or saved. */
    data object Failed : RecordingEvent
}

/**
 * Blanks out entries that carry no title. Besides empty lines this covers the literal string
 * "null", which older versions wrote whenever a stream sent no metadata at all and which may
 * still sit in a user's stored history.
 */
fun String.sanitizedMetadata(): String = trim().takeUnless { it.isEmpty() || it == "null" }.orEmpty()

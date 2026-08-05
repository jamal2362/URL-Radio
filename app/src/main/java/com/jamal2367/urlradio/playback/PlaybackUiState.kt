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
) {

    /** The newest metadata line, falling back to an empty string. */
    val currentMetadata: String get() = metadataHistory.lastOrNull().orEmpty().sanitizedMetadata()
}

/**
 * Blanks out entries that carry no title. Besides empty lines this covers the literal string
 * "null", which older versions wrote whenever a stream sent no metadata at all and which may
 * still sit in a user's stored history.
 */
fun String.sanitizedMetadata(): String = trim().takeUnless { it.isEmpty() || it == "null" }.orEmpty()

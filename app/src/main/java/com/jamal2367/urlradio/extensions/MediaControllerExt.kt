/*
 * MediaControllerExt.kt
 * Implements the MediaControllerExt extension methods
 * Useful extension methods for MediaController
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.extensions

import android.content.Context
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.core.Station
import com.jamal2367.urlradio.helpers.CollectionHelper


/* Starts the sleep timer */
fun MediaController.startSleepTimer() {
    sendCustomCommand(SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY), Bundle.EMPTY)
}


/* Cancels the sleep timer */
fun MediaController.cancelSleepTimer() {
    sendCustomCommand(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY), Bundle.EMPTY)
}


/* Request sleep timer remaining */
fun MediaController.requestSleepTimerRemaining(): ListenableFuture<SessionResult> {
    return sendCustomCommand(
        SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY),
        Bundle.EMPTY
    )
}


/* Request sleep timer remaining */
fun MediaController.requestMetadataHistory(): ListenableFuture<SessionResult> {
    return sendCustomCommand(
        SessionCommand(Keys.CMD_REQUEST_METADATA_HISTORY, Bundle.EMPTY),
        Bundle.EMPTY
    )
}


/* Starts playback with a new media item */
fun MediaController.play(context: Context, station: Station) {
    if (isPlaying) pause()
    // set media item, prepare and play
    setMediaItem(CollectionHelper.buildMediaItem(context, station))
    prepare()
    play()
}


/* Starts playback with of a stream url */
fun MediaController.playStreamDirectly(streamUri: String) {
    sendCustomCommand(
        SessionCommand(Keys.CMD_PLAY_STREAM, Bundle.EMPTY),
        bundleOf(Pair(Keys.KEY_STREAM_URI, streamUri))
    )
}


/* Returns mediaId of currently active media item */
fun MediaController.getCurrentMediaId(): String {
    return if (mediaItemCount > 0) {
        getMediaItemAt(0).mediaId
    } else {
        String()
    }
}


/* Returns if controller/player has one or more media items  */
fun MediaController.hasMediaItems(): Boolean {
    return mediaItemCount > 0
}
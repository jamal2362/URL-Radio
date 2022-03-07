/*
 * AudioHelper.kt
 * Implements the AudioHelper object
 * A AudioHelper provides helper methods for handling audio files
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.helpers

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri


/*
 * AudioHelper object
 */
object AudioHelper {

    /* Extract duration metadata from audio file */
    fun getDuration(context: Context, audioFileUri: Uri): Long {
        val metadataRetriever = MediaMetadataRetriever()
        var duration = 0L
        try {
            metadataRetriever.setDataSource(context, audioFileUri)
            val durationString = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: String()
            duration = durationString.toLong()
        } catch (exception: Exception) {
            LogHelper.e("Unable to extract duration metadata from audio file")
        }
        return duration
    }

}

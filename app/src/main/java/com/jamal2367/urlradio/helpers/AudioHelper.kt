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
import android.util.Log
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.jamal2367.urlradio.Keys
import kotlin.math.min


/*
 * AudioHelper object
 */
object AudioHelper {


    /* Define log tag */
    private val TAG: String = AudioHelper::class.java.simpleName


    /* Extract duration metadata from audio file */
    fun getDuration(context: Context, audioFileUri: Uri): Long {
        val metadataRetriever = MediaMetadataRetriever()
        var duration = 0L
        try {
            metadataRetriever.setDataSource(context, audioFileUri)
            val durationString =
                metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?: String()
            duration = durationString.toLong()
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to extract duration metadata from audio file")
        }
        return duration
    }


    /* Extract audio stream metadata */
    fun getMetadataString(metadata: Metadata): String {
        var metadataString = String()
        for (i in 0 until metadata.length()) {
            // extract IceCast metadata
            when (val entry = metadata.get(i)) {
                is IcyInfo -> {
                    metadataString = entry.title.toString()
                }

                is IcyHeaders -> {
                    Log.i(TAG, "icyHeaders:" + entry.name + " - " + entry.genre)
                }

                else -> {
                    Log.w(TAG, "Unsupported metadata received (type = ${entry.javaClass.simpleName})")
                }
            }
            // TODO implement HLS metadata extraction (Id3Frame / PrivFrame)
            // https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/metadata/Metadata.Entry.html
        }
        // ensure a max length of the metadata string
        if (metadataString.isNotEmpty()) {
            metadataString = metadataString.substring(0, min(metadataString.length, Keys.DEFAULT_MAX_LENGTH_OF_METADATA_ENTRY))
        }
        return metadataString
    }


}

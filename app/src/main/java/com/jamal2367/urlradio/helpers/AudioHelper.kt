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

import android.util.Log
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.jamal2367.urlradio.Keys
import java.util.Locale
import kotlin.math.min


/*
 * AudioHelper object
 *
 * Reads ICY metadata, which media3 still marks as unstable. The opt-in is declared here
 * rather than suppressed at each call site.
 */
@UnstableApi
object AudioHelper {


    /* Define log tag */
    private val tag: String = AudioHelper::class.java.simpleName


    /* Extract audio stream metadata */
    fun getMetadataString(metadata: Metadata): String {
        var metadataString = String()
        // HLS (ID3) and Ogg (Vorbis comments) deliver title and artist separately
        var title = String()
        var artist = String()
        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                // Icecast / Shoutcast: "Artist - Title" in one string. A missing title used
                // to end up as the text "null".
                is IcyInfo -> {
                    metadataString = entry.title.orEmpty()
                }

                is IcyHeaders -> {
                    Log.i(tag, "icyHeaders:" + entry.name + " - " + entry.genre)
                }

                // HLS: ID3 frames in the segments - TIT2 is the title, TPE1 the artist.
                // Some servers put "Artist - Title" into a TXXX frame named StreamTitle.
                is TextInformationFrame -> {
                    val value: String = entry.values.firstOrNull().orEmpty().trim()
                    when {
                        entry.id == "TIT2" -> title = value
                        entry.id == "TPE1" -> artist = value
                        entry.id == "TXXX" && entry.description.equals("StreamTitle", ignoreCase = true) -> metadataString = value
                    }
                }

                // Ogg streams (Vorbis, Opus, FLAC): comments such as TITLE=... and ARTIST=...
                is VorbisComment -> {
                    when (entry.key.uppercase(Locale.ROOT)) {
                        "TITLE" -> title = entry.value.trim()
                        "ARTIST" -> artist = entry.value.trim()
                    }
                }

                else -> {
                    Log.w(
                        tag,
                        "Unsupported metadata received (type = ${entry.javaClass.simpleName})"
                    )
                }
            }
        }
        // the same "Artist - Title" form Icecast stations use
        if (metadataString.isEmpty()) {
            metadataString = listOf(artist, title).filter { it.isNotEmpty() }.joinToString(" - ")
        }
        // ensure a max length of the metadata string
        if (metadataString.isNotEmpty()) {
            metadataString = metadataString.substring(0, min(metadataString.length, Keys.DEFAULT_MAX_LENGTH_OF_METADATA_ENTRY))
        }
        return metadataString
    }


}

/*
 * RecordingFormat.kt
 * The file formats a recording can be saved in
 *
 * Only formats Android can encode on its own, so the app needs no extra encoder library.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.recording

import android.os.Build


enum class RecordingFormat(
    /** Stored in the preferences - never change it. */
    val key: String,
    val extension: String,
    val mimeType: String,
    /** Selectable bit rates in kbit/s. Empty for an uncompressed format. */
    val bitratesKbps: List<Int>,
    val defaultBitrateKbps: Int,
    private val minSdk: Int,
) {
    /** AAC-LC in an MP4 container - plays everywhere. */
    AAC("aac", ".m4a", "audio/mp4", listOf(64, 96, 128, 160, 192, 256, 320), 192, Build.VERSION_CODES.M),

    /** Opus in an Ogg container - better sound per kbit/s. Android 10 added the encoder and the Ogg muxer. */
    OPUS("opus", ".ogg", "audio/ogg", listOf(48, 64, 96, 128, 160, 192, 256), 128, Build.VERSION_CODES.Q),

    /** Uncompressed 16 bit PCM - lossless, but large. */
    WAV("wav", ".wav", "audio/x-wav", emptyList(), 0, Build.VERSION_CODES.M);

    /** Whether this device can record in this format. */
    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= minSdk

    /** Whether a bit rate can be chosen for this format. */
    val hasBitrate: Boolean
        get() = bitratesKbps.isNotEmpty()

    /** The given bit rate if this format offers it, otherwise the format's default. */
    fun coerceBitrate(kbps: Int): Int = if (kbps in bitratesKbps) kbps else defaultBitrateKbps

    companion object {
        /** The format stored under [key] - AAC when it is unknown or not available here. */
        fun fromKey(key: String?): RecordingFormat =
            entries.firstOrNull { it.key == key && it.isAvailable } ?: AAC

        /** The formats this device can record in. */
        val available: List<RecordingFormat>
            get() = entries.filter { it.isAvailable }
    }
}

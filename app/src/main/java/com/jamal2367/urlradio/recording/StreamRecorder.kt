/*
 * StreamRecorder.kt
 * Records what the player is playing into AAC (.m4a) files
 *
 * The recorder sits in the player's audio chain as the sink of a TeeAudioProcessor, so it
 * receives exactly the decoded audio that goes to the speaker -- in step with what the
 * listener hears rather than with what the network has already buffered ahead, and the same
 * way for every stream type the player understands (MP3, AAC, Ogg/Opus, FLAC, HLS ...).
 *
 * The audio is copied off the playback thread and encoded on a thread of its own, so the
 * recorder can never stall playback. Each part is first written to the cache and only handed
 * to the music folder (see RecordingStorage) once it is complete.
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.recording

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt


@UnstableApi
class StreamRecorder(
    private val context: Context,
    private val listener: Listener,
) : TeeAudioProcessor.AudioBufferSink {

    /* Callbacks, always delivered on the main thread. */
    interface Listener {
        /** A finished part was saved under the given file name. */
        fun onRecordingSaved(fileName: String)
        /** Recording failed and has been stopped. */
        fun onRecordingFailed()
    }

    private val tag: String = StreamRecorder::class.java.simpleName

    private val thread = HandlerThread("StreamRecorder").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tempFolder = File(context.cacheDir, TEMP_FOLDER)

    /* Read from any thread; written on the main thread only. */
    @Volatile
    var isRecording: Boolean = false
        private set
    @Volatile
    private var startedAt: Long = 0L

    /** Milliseconds since the recording was started, 0 while not recording. */
    val elapsedMillis: Long
        get() = if (isRecording) SystemClock.elapsedRealtime() - startedAt else 0L

    /* Recorder thread only. */
    private var active: Boolean = false
    private var sampleRate: Int = 0
    private var channelCount: Int = 0
    private var encoding: Int = C.ENCODING_INVALID
    private var stationName: String = String()
    private var title: String = String()
    private var splitByTitle: Boolean = false
    private var part: Part? = null


    init {
        // whatever is left in the cache belongs to a recording that was cut off by a crash
        handler.post { tempFolder.listFiles()?.forEach { it.delete() } }
    }


    /* Starts recording. Main thread. */
    fun start(stationName: String, title: String, splitByTitle: Boolean) {
        if (isRecording) return
        isRecording = true
        startedAt = SystemClock.elapsedRealtime()
        handler.post {
            this.stationName = stationName
            this.title = title
            this.splitByTitle = splitByTitle
            active = true
        }
    }


    /* Stops recording and saves what has been recorded. Main thread. */
    fun stop() {
        if (!isRecording) return
        isRecording = false
        handler.post {
            active = false
            closePart()
        }
    }


    /* Tells the recorder that the stream announced a new title. Main thread. */
    fun onTitleChanged(newTitle: String) {
        if (!isRecording || newTitle.isBlank()) return
        handler.post {
            if (!active || newTitle == title) return@post
            title = newTitle
            // the next audio buffer opens the part for the new title
            if (splitByTitle) closePart()
        }
    }


    /* Finishes any recording and ends the recorder thread. Main thread. */
    fun release() {
        stop()
        thread.quitSafely()
    }


    /* TeeAudioProcessor.AudioBufferSink - playback thread: the format of the audio to come. */
    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        handler.post {
            if (sampleRateHz == sampleRate && channelCount == this.channelCount && encoding == this.encoding) return@post
            // a part cannot change its format midway, so a new one is begun
            closePart()
            sampleRate = sampleRateHz
            this.channelCount = channelCount
            this.encoding = encoding
        }
    }


    /* TeeAudioProcessor.AudioBufferSink - playback thread: audio on its way to the speaker. */
    override fun handleBuffer(buffer: ByteBuffer) {
        if (!isRecording || !buffer.hasRemaining()) return
        val audio = ByteArray(buffer.remaining())
        buffer.get(audio)
        handler.post { write(audio) }
    }


    /* Recorder thread: encodes a chunk of audio, opening a part when none is open. */
    private fun write(audio: ByteArray) {
        if (!active) return
        val pcm: ByteArray = when (encoding) {
            C.ENCODING_PCM_16BIT -> audio
            C.ENCODING_PCM_FLOAT -> floatToPcm16(audio)
            else -> {
                Log.w(tag, "Unsupported PCM encoding $encoding - recording stopped.")
                fail()
                return
            }
        }
        try {
            val currentPart: Part = part ?: openPart().also { part = it }
            currentPart.write(pcm)
        } catch (e: Exception) {
            Log.e(tag, "Unable to encode recording.", e)
            fail()
        }
    }


    /* Recorder thread: begins a new part with the current format, station and title. */
    private fun openPart(): Part {
        tempFolder.mkdirs()
        val date: String = SimpleDateFormat(DATE_PATTERN, Locale.US).format(Date())
        val nameParts: List<String> = if (splitByTitle && title.isNotBlank() && title != stationName) {
            listOf(stationName, title, date)
        } else {
            listOf(stationName, date)
        }
        val displayName: String = nameParts.filter { it.isNotBlank() }
            .joinToString(" - ") { sanitize(it) } + FILE_EXTENSION
        val file = File(tempFolder, "${System.nanoTime()}$FILE_EXTENSION")
        return Part(file, displayName, sampleRate, channelCount)
    }


    /* Recorder thread: finishes the open part and saves it. */
    private fun closePart() {
        val finished: Part = part ?: return
        part = null
        val complete: Boolean = runCatching { finished.finish() }.getOrElse {
            Log.e(tag, "Unable to finish recording.", it)
            false
        }
        if (complete) {
            val saved: Boolean = RecordingStorage.save(context, finished.file, finished.displayName)
            mainHandler.post {
                if (saved) listener.onRecordingSaved(finished.displayName) else listener.onRecordingFailed()
            }
        }
        finished.file.delete()
    }


    /* Recorder thread: gives up on the open part and stops recording. */
    private fun fail() {
        active = false
        part?.let {
            it.abandon()
            it.file.delete()
        }
        part = null
        mainHandler.post {
            isRecording = false
            listener.onRecordingFailed()
        }
    }


    /* Converts 32 bit float PCM to 16 bit PCM, which is what the AAC encoder takes. */
    private fun floatToPcm16(audio: ByteArray): ByteArray {
        val input = ByteBuffer.wrap(audio).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val output = ByteBuffer.allocate(input.remaining() * 2).order(ByteOrder.nativeOrder())
        while (input.hasRemaining()) {
            val sample: Float = input.get().coerceIn(-1f, 1f)
            output.putShort((sample * Short.MAX_VALUE).roundToInt().toShort())
        }
        return output.array()
    }


    /* Removes characters that file systems or MediaStore do not accept in a file name. */
    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(MAX_NAME_LENGTH)


    /*
     * One recorded file: an AAC encoder feeding an MP4 muxer.
     */
    private inner class Part(
        val file: File,
        val displayName: String,
        private val sampleRate: Int,
        private val channelCount: Int,
    ) {
        private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        private val muxer: MediaMuxer = try {
            MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            codec.release()
            throw e
        }
        private val bufferInfo = MediaCodec.BufferInfo()
        private val frameSize: Int = channelCount * BYTES_PER_SAMPLE
        private var track: Int = -1
        private var muxerStarted: Boolean = false
        private var framesQueued: Long = 0L

        init {
            try {
                val format: MediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, min(channelCount, 2) * BIT_RATE_PER_CHANNEL)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
                }
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
            } catch (e: Exception) {
                abandon()
                file.delete()
                throw e
            }
        }

        private val presentationTimeUs: Long
            get() = framesQueued * 1_000_000L / sampleRate

        fun write(pcm: ByteArray) {
            var offset = 0
            var attempts = 0
            while (offset < pcm.size) {
                val index: Int = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index < 0) {
                    drain(endOfStream = false)
                    if (++attempts > MAX_ATTEMPTS) throw IllegalStateException("Encoder does not accept input.")
                    continue
                }
                attempts = 0
                val input: ByteBuffer = codec.getInputBuffer(index) ?: throw IllegalStateException("No input buffer.")
                input.clear()
                var length: Int = min(input.remaining(), pcm.size - offset)
                length -= length % frameSize
                input.put(pcm, offset, length)
                codec.queueInputBuffer(index, 0, length, presentationTimeUs, 0)
                framesQueued += length / frameSize
                offset += length
                drain(endOfStream = false)
            }
        }

        /* Returns whether the part holds enough audio to be worth keeping. */
        fun finish(): Boolean {
            var index: Int = -1
            for (attempt in 0..MAX_ATTEMPTS) {
                index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) break
                drain(endOfStream = false)
            }
            if (index >= 0) {
                codec.queueInputBuffer(index, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(endOfStream = true)
            }
            val started: Boolean = muxerStarted
            codec.stop()
            codec.release()
            if (started) muxer.stop()
            muxer.release()
            return started && presentationTimeUs >= MIN_DURATION_US
        }

        fun abandon() {
            runCatching { codec.release() }
            runCatching { muxer.release() }
        }

        private fun drain(endOfStream: Boolean) {
            var attempts = 0
            while (true) {
                val index: Int = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) TIMEOUT_US else 0L)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream || ++attempts > MAX_ATTEMPTS) return
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    index >= 0 -> {
                        val output: ByteBuffer? = codec.getOutputBuffer(index)
                        val isConfig: Boolean = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (output != null && !isConfig && bufferInfo.size > 0 && muxerStarted) {
                            output.position(bufferInfo.offset)
                            output.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(track, output, bufferInfo)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
    }


    private companion object {
        const val TEMP_FOLDER = "recordings"
        const val FILE_EXTENSION = ".m4a"
        const val DATE_PATTERN = "yyyy-MM-dd HH.mm.ss"
        const val MAX_NAME_LENGTH = 80
        const val BYTES_PER_SAMPLE = 2
        const val BIT_RATE_PER_CHANNEL = 96_000 // 192 kbit/s for stereo
        const val MAX_INPUT_SIZE = 64 * 1024
        const val TIMEOUT_US = 10_000L
        const val MAX_ATTEMPTS = 100
        const val MIN_DURATION_US = 1_000_000L // shorter leftovers of a title change are dropped
    }
}

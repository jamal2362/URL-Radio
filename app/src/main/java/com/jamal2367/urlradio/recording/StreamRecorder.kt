/*
 * StreamRecorder.kt
 * Records what the player is playing into audio files
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
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.io.File
import java.io.RandomAccessFile
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
    private var format: RecordingFormat = RecordingFormat.AAC
    private var bitrateKbps: Int = RecordingFormat.AAC.defaultBitrateKbps
    private var part: Part? = null


    init {
        // whatever is left in the cache belongs to a recording that was cut off by a crash
        handler.post { tempFolder.listFiles()?.forEach { it.delete() } }
    }


    /* Starts recording. Main thread. */
    fun start(stationName: String, format: RecordingFormat, bitrateKbps: Int) {
        if (isRecording) return
        isRecording = true
        startedAt = SystemClock.elapsedRealtime()
        handler.post {
            this.stationName = stationName
            this.format = format
            this.bitrateKbps = format.coerceBitrate(bitrateKbps)
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


    /* Recorder thread: begins a new part with the current format and station. */
    private fun openPart(): Part {
        tempFolder.mkdirs()
        val date: String = SimpleDateFormat(DATE_PATTERN, Locale.US).format(Date())
        val displayName: String = listOf(stationName, date).filter { it.isNotBlank() }
            .joinToString(" - ") { sanitize(it) } + format.extension
        val file = File(tempFolder, "${System.nanoTime()}${format.extension}")
        return when (format) {
            RecordingFormat.AAC -> CodecPart(
                file, displayName, sampleRate, channelCount,
                mimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
                muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                bitrate = bitrateKbps * 1000,
                encoderSampleRate = sampleRate,
            )
            RecordingFormat.OPUS -> CodecPart(
                file, displayName, sampleRate, channelCount,
                mimeType = MediaFormat.MIMETYPE_AUDIO_OPUS,
                muxerFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
                    else throw IllegalStateException("Opus needs Android 10."),
                bitrate = bitrateKbps * 1000,
                // Opus only takes these rates - everything else (like the common 44.1 kHz) is resampled
                encoderSampleRate = if (sampleRate in OPUS_SAMPLE_RATES) sampleRate else OPUS_DEFAULT_SAMPLE_RATE,
            )
            RecordingFormat.WAV -> WavPart(file, displayName, sampleRate, channelCount)
        }
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
            val saved: Boolean = RecordingStorage.save(context, finished.file, finished.displayName, format.mimeType)
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


    /* Converts 32 bit float PCM to 16 bit PCM, which is what the encoders take. */
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
     * One recorded file. Takes 16 bit PCM in the stream's own sample rate and channel count.
     */
    private abstract class Part(val file: File, val displayName: String) {
        abstract fun write(pcm: ByteArray)
        /* Returns whether the part holds enough audio to be worth keeping. */
        abstract fun finish(): Boolean
        abstract fun abandon()
    }


    /*
     * A compressed part: a MediaCodec encoder feeding a MediaMuxer. When the encoder needs a
     * different sample rate than the stream has, a Sonic resampler sits in front of it.
     */
    private class CodecPart(
        file: File,
        displayName: String,
        sampleRate: Int,
        channelCount: Int,
        mimeType: String,
        muxerFormat: Int,
        bitrate: Int,
        private val encoderSampleRate: Int,
    ) : Part(file, displayName) {
        private val codec: MediaCodec = MediaCodec.createEncoderByType(mimeType)
        private val muxer: MediaMuxer = try {
            MediaMuxer(file.absolutePath, muxerFormat)
        } catch (e: Exception) {
            codec.release()
            throw e
        }
        private val resampler: SonicAudioProcessor? =
            if (encoderSampleRate == sampleRate) null else SonicAudioProcessor().apply {
                setOutputSampleRateHz(encoderSampleRate)
                configure(AudioProcessor.AudioFormat(sampleRate, channelCount, C.ENCODING_PCM_16BIT))
                flush(AudioProcessor.StreamMetadata.DEFAULT)
            }
        private val bufferInfo = MediaCodec.BufferInfo()
        private val frameSize: Int = channelCount * BYTES_PER_SAMPLE
        private var track: Int = -1
        private var muxerStarted: Boolean = false
        private var framesQueued: Long = 0L

        init {
            try {
                val format: MediaFormat = MediaFormat.createAudioFormat(mimeType, encoderSampleRate, channelCount).apply {
                    if (mimeType == MediaFormat.MIMETYPE_AUDIO_AAC) {
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    }
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
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
            get() = framesQueued * 1_000_000L / encoderSampleRate

        override fun write(pcm: ByteArray) {
            val sonic: SonicAudioProcessor = resampler ?: return encode(pcm)
            sonic.queueInput(ByteBuffer.wrap(pcm).order(ByteOrder.nativeOrder()))
            encodeResampled(sonic)
        }

        override fun finish(): Boolean {
            resampler?.let { sonic ->
                sonic.queueEndOfStream()
                encodeResampled(sonic)
                sonic.reset()
            }
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

        override fun abandon() {
            runCatching { resampler?.reset() }
            runCatching { codec.release() }
            runCatching { muxer.release() }
        }

        private fun encodeResampled(sonic: SonicAudioProcessor) {
            val output: ByteBuffer = sonic.output
            if (!output.hasRemaining()) return
            val resampled = ByteArray(output.remaining())
            output.get(resampled)
            encode(resampled)
        }

        private fun encode(pcm: ByteArray) {
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


    /*
     * An uncompressed part: a RIFF/WAVE header followed by the 16 bit PCM as it is. The sizes
     * in the header are only known at the end, so they are filled in by finish().
     */
    private class WavPart(
        file: File,
        displayName: String,
        private val sampleRate: Int,
        private val channelCount: Int,
    ) : Part(file, displayName) {
        private val output = RandomAccessFile(file, "rw")
        private var dataBytes: Long = 0L

        init {
            output.setLength(0)
            output.write(header(0))
        }

        override fun write(pcm: ByteArray) {
            // WAV is little endian; so is every Android device, but better safe than noisy
            val bytes: ByteArray = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) pcm else swapBytes(pcm)
            output.write(bytes)
            dataBytes += bytes.size
        }

        override fun finish(): Boolean {
            output.seek(0)
            output.write(header(dataBytes))
            output.close()
            val bytesPerSecond: Long = sampleRate.toLong() * channelCount * BYTES_PER_SAMPLE
            return dataBytes * 1_000_000L / bytesPerSecond >= MIN_DURATION_US
        }

        override fun abandon() {
            runCatching { output.close() }
        }

        private fun header(dataSize: Long): ByteArray {
            val size: Int = min(dataSize, MAX_WAV_DATA_SIZE).toInt()
            return ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(WAV_HEADER_SIZE - 8 + size)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)                                                  // size of the fmt chunk
                putShort(1)                                                 // PCM
                putShort(channelCount.toShort())
                putInt(sampleRate)
                putInt(sampleRate * channelCount * BYTES_PER_SAMPLE)        // byte rate
                putShort((channelCount * BYTES_PER_SAMPLE).toShort())       // block align
                putShort((BYTES_PER_SAMPLE * 8).toShort())                  // bits per sample
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(size)
            }.array()
        }

        private fun swapBytes(pcm: ByteArray): ByteArray = ByteArray(pcm.size).also { swapped ->
            for (i in 0 until pcm.size - 1 step 2) {
                swapped[i] = pcm[i + 1]
                swapped[i + 1] = pcm[i]
            }
        }
    }


    private companion object {
        const val TEMP_FOLDER = "recordings"
        const val DATE_PATTERN = "yyyy-MM-dd HH.mm.ss"
        const val MAX_NAME_LENGTH = 80
        const val BYTES_PER_SAMPLE = 2
        const val MAX_INPUT_SIZE = 64 * 1024
        const val TIMEOUT_US = 10_000L
        const val MAX_ATTEMPTS = 100
        const val MIN_DURATION_US = 1_000_000L // shorter parts are not worth a file
        val OPUS_SAMPLE_RATES = setOf(8_000, 12_000, 16_000, 24_000, 48_000)
        const val OPUS_DEFAULT_SAMPLE_RATE = 48_000
        const val WAV_HEADER_SIZE = 44
        const val MAX_WAV_DATA_SIZE = 0xFFFFFFFFL - WAV_HEADER_SIZE // the RIFF size field is 32 bit
    }
}

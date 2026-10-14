package com.eonmirth.smartcapture.recording

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** Microphone → AAC → CaptureMuxer. Requires RECORD_AUDIO. */
class MicAudioEncoder(
    private val muxer: CaptureMuxer
) {
    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var trackIndex = -1
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    fun start(): Boolean {
        if (running.getAndSet(true)) return true

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuf <= 0) {
            running.set(false)
            return false
        }

        val record = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBuf * 2)
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO not granted", e)
            running.set(false)
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            running.set(false)
            return false
        }

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBuf * 2)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        audioRecord = record
        codec = encoder
        record.startRecording()
        thread = Thread({ loop(minBuf) }, "SmartCapture-Mic").also { it.start() }
        Log.i(TAG, "Mic AAC encoder started")
        return true
    }

    private fun loop(chunkSize: Int) {
        val pcm = ByteArray(chunkSize)
        val bufferInfo = MediaCodec.BufferInfo()
        var presentationTimeUs = 0L
        val bytesPerSecond = 44100 * 2

        while (running.get()) {
            val c = codec ?: break
            val rec = audioRecord ?: break
            val read = try { rec.read(pcm, 0, pcm.size) } catch (e: Exception) {
                Log.e(TAG, "read", e); break
            }
            if (read > 0) {
                val inIndex = c.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    c.getInputBuffer(inIndex)?.apply {
                        clear()
                        put(pcm, 0, read)
                    }
                    val pts = presentationTimeUs
                    presentationTimeUs += (read * 1_000_000L) / bytesPerSecond
                    c.queueInputBuffer(inIndex, 0, read, pts, 0)
                }
            }
            while (true) {
                val outIndex = c.dequeueOutputBuffer(bufferInfo, 0)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (trackIndex < 0) trackIndex = muxer.addTrack(c.outputFormat)
                    }
                    outIndex >= 0 -> {
                        val outBuf = c.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0 && trackIndex >= 0 &&
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSample(trackIndex, outBuf, bufferInfo)
                        }
                        c.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            running.set(false)
                            break
                        }
                    }
                    else -> break
                }
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false) && codec == null) {
            releaseQuietly()
            return
        }
        try {
            codec?.let { c ->
                val inIndex = c.dequeueInputBuffer(10_000)
                if (inIndex >= 0) c.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        } catch (_: Exception) { }
        try { thread?.join(500) } catch (_: Exception) { }
        releaseQuietly()
    }

    private fun releaseQuietly() {
        try { audioRecord?.stop() } catch (_: Exception) { }
        try { audioRecord?.release() } catch (_: Exception) { }
        audioRecord = null
        try { codec?.stop() } catch (_: Exception) { }
        try { codec?.release() } catch (_: Exception) { }
        codec = null
        thread = null
        trackIndex = -1
    }

    companion object {
        private const val TAG = "MicAudioEncoder"
    }
}

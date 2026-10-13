package com.eonmirth.smartcapture.recording

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Surface-input H.264 encoder + MediaMuxer → MP4.
 * Phase 4: video only, no audio.
 */
class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val bitrate: Int,
    private val outputFile: File
) {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private val running = AtomicBoolean(false)
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    val surface: Surface?
        get() = inputSurface

    fun start() {
        if (running.getAndSet(true)) return

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = encoder.createInputSurface()
        encoder.start()
        codec = encoder

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val t = HandlerThread("SmartCapture-Encoder").also { it.start() }
        thread = t
        handler = Handler(t.looper)
        handler?.post { drainLoop() }
        Log.i(TAG, "Encoder started ${width}x${height}@${frameRate} → ${outputFile.name}")
    }

    private fun drainLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (running.get()) {
            val c = codec ?: break
            try {
                val outIndex = c.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> { }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            val newFormat = c.outputFormat
                            trackIndex = muxer!!.addTrack(newFormat)
                            muxer!!.start()
                            muxerStarted = true
                            Log.i(TAG, "Muxer started, track=$trackIndex")
                        }
                    }
                    outIndex >= 0 -> {
                        val encoded: ByteBuffer? = c.getOutputBuffer(outIndex)
                        if (encoded != null && bufferInfo.size > 0 && muxerStarted) {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                encoded.position(bufferInfo.offset)
                                encoded.limit(bufferInfo.offset + bufferInfo.size)
                                muxer!!.writeSampleData(trackIndex, encoded, bufferInfo)
                            }
                        }
                        c.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            running.set(false)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "drainLoop error", e)
                running.set(false)
                break
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false) && codec == null) {
            releaseQuietly()
            return
        }
        try {
            codec?.signalEndOfInputStream()
        } catch (e: Exception) {
            Log.w(TAG, "signalEndOfInputStream", e)
        }
        try {
            Thread.sleep(200)
        } catch (_: InterruptedException) { }
        releaseQuietly()
        Log.i(TAG, "Encoder stopped, file size=${outputFile.length()}")
    }

    private fun releaseQuietly() {
        try {
            if (muxerStarted) {
                try { muxer?.stop() } catch (e: Exception) { Log.w(TAG, "muxer.stop", e) }
            }
            muxer?.release()
        } catch (_: Exception) { }
        muxer = null
        muxerStarted = false
        trackIndex = -1
        try { codec?.stop() } catch (_: Exception) { }
        try { codec?.release() } catch (_: Exception) { }
        codec = null
        try { inputSurface?.release() } catch (_: Exception) { }
        inputSurface = null
        try { thread?.quitSafely() } catch (_: Exception) { }
        thread = null
        handler = null
    }

    companion object {
        private const val TAG = "VideoEncoder"
    }
}

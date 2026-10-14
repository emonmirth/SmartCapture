package com.eonmirth.smartcapture.recording

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Shared MediaMuxer for video (+ optional audio). */
class CaptureMuxer(
    outputFile: File,
    private val expectedTracks: Int
) {
    private val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val started = AtomicBoolean(false)
    private val tracksAdded = AtomicInteger(0)
    private val lock = Any()

    fun addTrack(format: MediaFormat): Int = synchronized(lock) {
        val index = muxer.addTrack(format)
        val count = tracksAdded.incrementAndGet()
        Log.i(TAG, "Track added index=$index ($count/$expectedTracks)")
        if (!started.get() && count >= expectedTracks) {
            muxer.start()
            started.set(true)
            Log.i(TAG, "Muxer started")
        }
        return index
    }

    fun writeSample(trackIndex: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (!started.get() || trackIndex < 0) return
            try {
                muxer.writeSampleData(trackIndex, buffer, info)
            } catch (e: Exception) {
                Log.e(TAG, "writeSample", e)
            }
        }
    }

    fun forceStartIfNeeded() {
        synchronized(lock) {
            if (!started.get() && tracksAdded.get() >= 1) {
                try {
                    muxer.start()
                    started.set(true)
                    Log.i(TAG, "Muxer force-started with ${tracksAdded.get()} track(s)")
                } catch (e: Exception) {
                    Log.e(TAG, "forceStart", e)
                }
            }
        }
    }

    fun stopAndRelease() {
        synchronized(lock) {
            try {
                if (started.get()) muxer.stop()
            } catch (e: Exception) {
                Log.w(TAG, "muxer.stop", e)
            }
            try { muxer.release() } catch (_: Exception) { }
            started.set(false)
        }
    }

    fun isStarted(): Boolean = started.get()

    companion object {
        private const val TAG = "CaptureMuxer"
    }
}

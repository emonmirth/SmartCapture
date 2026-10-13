package com.eonmirth.smartcapture.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.eonmirth.smartcapture.MainActivity
import com.eonmirth.smartcapture.R
import com.eonmirth.smartcapture.capability.CapabilityProbe
import com.eonmirth.smartcapture.data.PreferencesRepository
import com.eonmirth.smartcapture.model.QualityProfile
import com.eonmirth.smartcapture.model.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service: MediaProjection + VirtualDisplay + VideoEncoder → MP4.
 * Phase 4: video only.
 */
class RecordingService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: VideoEncoder? = null
    private var tempFile: File? = null
    private var encodeWidth = 1280
    private var encodeHeight = 720
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (data == null) {
                    Log.e(TAG, "Missing result data")
                    ProjectionHolder.updateState(RecordingState.ERROR)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startAsForeground()
                scope.launch { startCapture(resultCode, data) }
            }
            ACTION_STOP -> {
                scope.launch { stopCaptureAndSelf(save = true) }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun startCapture(resultCode: Int, data: Intent) {
        ProjectionHolder.updateState(RecordingState.STARTING)

        val profile = resolveProfile()
        encodeWidth = profile.width
        encodeHeight = profile.height
        val bitrate = (profile.bitrateMbps * 1_000_000).toInt()

        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            mgr.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "getMediaProjection failed", e)
            failAndStop()
            return
        } ?: run {
            failAndStop()
            return
        }

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection.onStop")
                scope.launch { stopCaptureAndSelf(save = true) }
            }
        }
        projection.registerCallback(callback, null)
        projectionCallback = callback
        mediaProjection = projection
        ProjectionHolder.attach(projection)

        val out = RecordingOutput.createTempFile(this)
        tempFile = out

        val enc = try {
            VideoEncoder(
                width = encodeWidth,
                height = encodeHeight,
                frameRate = profile.frameRate,
                bitrate = bitrate,
                outputFile = out
            ).also { it.start() }
        } catch (e: Exception) {
            Log.e(TAG, "Encoder start failed", e)
            failAndStop()
            return
        }
        encoder = enc
        val surface = enc.surface
        if (surface == null) {
            Log.e(TAG, "No input surface")
            failAndStop()
            return
        }

        val density = resources.displayMetrics.densityDpi
        virtualDisplay = try {
            projection.createVirtualDisplay(
                "SmartCapture",
                encodeWidth,
                encodeHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "VirtualDisplay failed", e)
            failAndStop()
            return
        }

        ProjectionHolder.updateState(RecordingState.RECORDING)
        updateNotification("Recording ${profile.displayName}")
        Log.i(TAG, "Capture started ${encodeWidth}x${encodeHeight}@${profile.frameRate}")
    }

    private suspend fun resolveProfile(): QualityProfile {
        return try {
            val prefs = PreferencesRepository(this).preferences.first()
            val cap = CapabilityProbe.probe(this)
            cap.supportedProfiles.find { it.preset == prefs.qualityPreset }
                ?: cap.supportedProfiles.firstOrNull()
                ?: QualityProfile.BatterySaver
        } catch (_: Exception) {
            QualityProfile.BatterySaver
        }
    }

    private suspend fun stopCaptureAndSelf(save: Boolean) {
        val state = ProjectionHolder.state.value
        if (state == RecordingState.IDLE || state == RecordingState.FINALIZING || state == RecordingState.SAVED) {
            if (state == RecordingState.IDLE) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return
        }

        ProjectionHolder.updateState(RecordingState.FINALIZING)
        updateNotification("Saving…")

        try { virtualDisplay?.release() } catch (_: Exception) { }
        virtualDisplay = null

        try { encoder?.stop() } catch (e: Exception) { Log.e(TAG, "encoder.stop", e) }
        encoder = null

        releaseProjectionOnly()

        var published = false
        if (save) {
            val file = tempFile
            if (file != null) {
                val uri = RecordingOutput.publishToMediaStore(this, file, encodeWidth, encodeHeight)
                published = uri != null
                if (published) Log.i(TAG, "Saved to MediaStore: $uri")
                else Log.w(TAG, "Publish failed or empty file")
            }
        } else {
            tempFile?.delete()
        }
        tempFile = null

        ProjectionHolder.updateState(if (published) RecordingState.SAVED else RecordingState.IDLE)
        if (published) {
            kotlinx.coroutines.delay(800)
            ProjectionHolder.updateState(RecordingState.IDLE)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun failAndStop() {
        scope.launch {
            try { virtualDisplay?.release() } catch (_: Exception) { }
            virtualDisplay = null
            try { encoder?.stop() } catch (_: Exception) { }
            encoder = null
            tempFile?.delete()
            tempFile = null
            releaseProjectionOnly()
            ProjectionHolder.updateState(RecordingState.ERROR)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun releaseProjectionOnly() {
        try { projectionCallback?.let { mediaProjection?.unregisterCallback(it) } } catch (_: Exception) { }
        try { mediaProjection?.stop() } catch (_: Exception) { }
        mediaProjection = null
        projectionCallback = null
        ProjectionHolder.detach()
    }

    override fun onDestroy() {
        scope.cancel()
        try { virtualDisplay?.release() } catch (_: Exception) { }
        try { encoder?.stop() } catch (_: Exception) { }
        releaseProjectionOnly()
        super.onDestroy()
    }

    private fun startAsForeground() {
        createChannel()
        val notification = buildNotification("Starting…")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while a capture session is active"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val TAG = "RecordingService"
        const val CHANNEL_ID = "smartcapture_recording"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.eonmirth.smartcapture.action.START"
        const val ACTION_STOP = "com.eonmirth.smartcapture.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}

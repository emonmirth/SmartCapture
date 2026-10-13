package com.eonmirth.smartcapture.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.eonmirth.smartcapture.MainActivity
import com.eonmirth.smartcapture.R
import com.eonmirth.smartcapture.model.RecordingState

/**
 * Foreground service required for MediaProjection (target SDK 34+).
 * Phase 3: holds the projection session, shows ongoing notification, handles stop/cleanup.
 * No encoder yet — that is Phase 4.
 */
class RecordingService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null

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
                    Log.e(TAG, "Missing result data for MediaProjection")
                    ProjectionHolder.updateState(RecordingState.ERROR)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startAsForeground()
                startProjection(resultCode, data)
            }
            ACTION_STOP -> {
                stopProjectionAndSelf()
            }
            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        createChannel()
        val notification = buildNotification("Recording session active")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            mgr.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "getMediaProjection failed", e)
            ProjectionHolder.updateState(RecordingState.ERROR)
            stopSelf()
            return
        }

        if (projection == null) {
            Log.e(TAG, "MediaProjection is null")
            ProjectionHolder.updateState(RecordingState.ERROR)
            stopSelf()
            return
        }

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection.onStop — system revoked or stopped")
                releaseProjectionOnly()
                ProjectionHolder.updateState(RecordingState.IDLE)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        projection.registerCallback(callback, null)
        projectionCallback = callback
        mediaProjection = projection
        ProjectionHolder.attach(projection)
        ProjectionHolder.updateState(RecordingState.RECORDING)
        Log.i(TAG, "MediaProjection session started (no encoder yet)")
    }

    private fun stopProjectionAndSelf() {
        releaseProjectionOnly()
        ProjectionHolder.updateState(RecordingState.IDLE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseProjectionOnly() {
        try {
            projectionCallback?.let { cb ->
                mediaProjection?.unregisterCallback(cb)
            }
        } catch (_: Exception) { }
        try {
            mediaProjection?.stop()
        } catch (_: Exception) { }
        mediaProjection = null
        projectionCallback = null
        ProjectionHolder.detach()
    }

    override fun onDestroy() {
        releaseProjectionOnly()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while a capture session is active"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
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
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

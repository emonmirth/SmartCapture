package com.eonmirth.smartcapture.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.eonmirth.smartcapture.MainActivity
import com.eonmirth.smartcapture.R
import com.eonmirth.smartcapture.capability.CapabilityProbe
import com.eonmirth.smartcapture.data.PreferencesRepository
import com.eonmirth.smartcapture.model.AudioOption
import com.eonmirth.smartcapture.model.QualityProfile
import com.eonmirth.smartcapture.model.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** Phase 4–8: capture + mic + visibility + notification/floating controls. */
class RecordingService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: VideoEncoder? = null
    private var captureMuxer: CaptureMuxer? = null
    private var micEncoder: MicAudioEncoder? = null
    private var includeMic = false
    private var tempFile: File? = null
    private var encodeWidth = 1280
    private var encodeHeight = 720
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var visibilityPolicy: VisibilityPolicy? = null
    private var floatingControls: FloatingControls? = null

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
                    RecorderController.tryTransitionSync(RecordingState.ERROR)
                    RecorderController.forceIdle("missing data")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startAsForeground()
                scope.launch { startCapture(resultCode, data) }
            }
            ACTION_STOP -> scope.launch { stopCaptureAndSelf(save = true) }
            ACTION_PAUSE -> {
                if (RecorderController.requestPauseByUser()) updateNotification("Paused")
            }
            ACTION_RESUME -> {
                if (RecorderController.requestResumeFromUserPause()) updateNotification("Recording…")
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun startCapture(resultCode: Int, data: Intent) {
        RecorderController.tryTransitionSync(RecordingState.STARTING)
        val profile = resolveProfile()
        encodeWidth = profile.width
        encodeHeight = profile.height
        val bitrate = (profile.bitrateMbps * 1_000_000).toInt()

        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            mgr.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "getMediaProjection failed", e)
            failAndStop(); return
        } ?: run { failAndStop(); return }

        val callback = object : MediaProjection.Callback() {
            override fun onStop() { scope.launch { stopCaptureAndSelf(save = true) } }
        }
        projection.registerCallback(callback, null)
        projectionCallback = callback
        mediaProjection = projection
        ProjectionHolder.attach(projection)

        val out = RecordingOutput.createTempFile(this)
        tempFile = out

        var wantMic = false
        try {
            val prefs = PreferencesRepository(this).preferences.first()
            wantMic = prefs.audioOption == AudioOption.MICROPHONE ||
                prefs.audioOption == AudioOption.MIC_AND_INTERNAL
        } catch (_: Exception) { }

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        includeMic = wantMic && hasMicPermission

        val muxer = CaptureMuxer(out, expectedTracks = if (includeMic) 2 else 1)
        captureMuxer = muxer

        val enc = try {
            VideoEncoder(encodeWidth, encodeHeight, profile.frameRate, bitrate, muxer).also { it.start() }
        } catch (e: Exception) {
            Log.e(TAG, "Encoder start failed", e)
            failAndStop(); return
        }
        encoder = enc
        val surface = enc.surface ?: run { failAndStop(); return }

        if (includeMic) {
            val mic = MicAudioEncoder(muxer)
            if (mic.start()) micEncoder = mic
            else { includeMic = false; muxer.forceStartIfNeeded() }
        }

        val density = resources.displayMetrics.densityDpi
        virtualDisplay = try {
            projection.createVirtualDisplay(
                "SmartCapture", encodeWidth, encodeHeight, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null
            )
        } catch (e: Exception) {
            Log.e(TAG, "VirtualDisplay failed", e)
            failAndStop(); return
        }

        RecorderController.tryTransitionSync(RecordingState.RECORDING)
        updateNotification(
            if (includeMic) "Recording ${profile.displayName} + mic"
            else "Recording ${profile.displayName}"
        )
        startVisibilityPolicy()
        maybeShowFloating()
        scope.launch {
            kotlinx.coroutines.delay(2500)
            captureMuxer?.forceStartIfNeeded()
        }
    }

    private fun maybeShowFloating() {
        scope.launch {
            try {
                val prefs = PreferencesRepository(this@RecordingService).preferences.first()
                if (!prefs.showFloatingControls) return@launch
                val fc = FloatingControls(this@RecordingService) {
                    scope.launch { stopCaptureAndSelf(save = true) }
                }
                fc.show()
                floatingControls = fc
            } catch (_: Exception) { }
        }
    }

    private fun startVisibilityPolicy() {
        visibilityPolicy?.stop()
        var enabled = true
        val policy = VisibilityPolicy(
            context = this,
            autoPauseEnabled = { enabled },
            onAutoPause = { updateNotification("Paused (screen off)") },
            onAutoResume = { updateNotification("Recording…") }
        )
        policy.start()
        visibilityPolicy = policy
        scope.launch {
            try {
                enabled = PreferencesRepository(this@RecordingService).preferences.first().autoPauseEnabled
                if (!enabled) { policy.stop(); visibilityPolicy = null }
            } catch (_: Exception) { }
        }
    }

    private suspend fun resolveProfile(): QualityProfile {
        return try {
            val prefs = PreferencesRepository(this).preferences.first()
            val cap = CapabilityProbe.probe(this)
            cap.supportedProfiles.find { it.preset == prefs.qualityPreset }
                ?: cap.supportedProfiles.firstOrNull()
                ?: QualityProfile.BatterySaver
        } catch (_: Exception) { QualityProfile.BatterySaver }
    }

    private suspend fun stopCaptureAndSelf(save: Boolean) {
        when (RecorderController.requestStop()) {
            RecorderController.StopRequestResult.ALREADY_STOPPED -> {
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
            }
            RecorderController.StopRequestResult.ALREADY_STOPPING -> return
            RecorderController.StopRequestResult.CANCELLED_CONSENT -> {
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
            }
            RecorderController.StopRequestResult.REJECTED -> return
            RecorderController.StopRequestResult.STOPPING -> { }
        }
        visibilityPolicy?.stop(); visibilityPolicy = null
        floatingControls?.hide(); floatingControls = null
        updateNotification("Saving…")

        try { virtualDisplay?.release() } catch (_: Exception) { }
        virtualDisplay = null
        try { micEncoder?.stop() } catch (_: Exception) { }
        micEncoder = null
        try { encoder?.stop() } catch (e: Exception) { Log.e(TAG, "encoder.stop", e) }
        encoder = null
        try { captureMuxer?.stopAndRelease() } catch (_: Exception) { }
        captureMuxer = null
        releaseProjectionOnly()

        var published = false
        if (save) {
            tempFile?.let {
                published = RecordingOutput.publishToMediaStore(this, it, encodeWidth, encodeHeight) != null
            }
        } else tempFile?.delete()
        tempFile = null

        if (published) {
            RecorderController.tryTransitionSync(RecordingState.SAVED)
            kotlinx.coroutines.delay(800)
        }
        RecorderController.forceIdle("after stop")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun failAndStop() {
        scope.launch {
            visibilityPolicy?.stop(); visibilityPolicy = null
            floatingControls?.hide(); floatingControls = null
            try { virtualDisplay?.release() } catch (_: Exception) { }
            virtualDisplay = null
            try { micEncoder?.stop() } catch (_: Exception) { }
            micEncoder = null
            try { encoder?.stop() } catch (_: Exception) { }
            encoder = null
            try { captureMuxer?.stopAndRelease() } catch (_: Exception) { }
            captureMuxer = null
            tempFile?.delete(); tempFile = null
            releaseProjectionOnly()
            RecorderController.tryTransitionSync(RecordingState.ERROR)
            RecorderController.forceIdle("fail")
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
        visibilityPolicy?.stop(); visibilityPolicy = null
        floatingControls?.hide(); floatingControls = null
        scope.cancel()
        try { virtualDisplay?.release() } catch (_: Exception) { }
        try { micEncoder?.stop() } catch (_: Exception) { }
        try { encoder?.stop() } catch (_: Exception) { }
        try { captureMuxer?.stopAndRelease() } catch (_: Exception) { }
        releaseProjectionOnly()
        super.onDestroy()
    }

    private fun startAsForeground() {
        createChannel()
        val notification = buildNotification("Starting…")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(content: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP), flags
        )
        val pauseIntent = PendingIntent.getService(
            this, 2, Intent(this, RecordingService::class.java).setAction(ACTION_PAUSE), flags
        )
        val resumeIntent = PendingIntent.getService(
            this, 3, Intent(this, RecordingService::class.java).setAction(ACTION_RESUME), flags
        )
        val state = RecorderController.currentState()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        when (state) {
            RecordingState.PAUSED_BY_USER, RecordingState.PAUSED_BY_SOURCE -> {
                builder.addAction(0, "Resume", resumeIntent)
                builder.addAction(0, "Stop", stopIntent)
            }
            RecordingState.RECORDING, RecordingState.STARTING -> {
                builder.addAction(0, "Pause", pauseIntent)
                builder.addAction(0, "Stop", stopIntent)
            }
            else -> builder.addAction(0, "Stop", stopIntent)
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "RecordingService"
        const val CHANNEL_ID = "smartcapture_recording"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.eonmirth.smartcapture.action.START"
        const val ACTION_STOP = "com.eonmirth.smartcapture.action.STOP"
        const val ACTION_PAUSE = "com.eonmirth.smartcapture.action.PAUSE"
        const val ACTION_RESUME = "com.eonmirth.smartcapture.action.RESUME"
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

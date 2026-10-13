package com.eonmirth.smartcapture.recording

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.eonmirth.smartcapture.model.RecordingState

/**
 * Handles the system MediaProjection consent dialog.
 * Phase 3: request → grant/deny/cancel → start or abort service.
 *
 * Every new session must request consent again. Do not reuse tokens.
 */
object ConsentHelper {

    fun createLauncher(
        activity: ComponentActivity,
        onResult: (granted: Boolean) -> Unit
    ): ActivityResultLauncher<Intent> {
        return activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                ProjectionHolder.updateState(RecordingState.STARTING)
                RecordingService.start(activity, result.resultCode, result.data!!)
                onResult(true)
            } else {
                ProjectionHolder.updateState(RecordingState.IDLE)
                onResult(false)
            }
        }
    }

    fun requestConsent(context: Context, launcher: ActivityResultLauncher<Intent>) {
        if (ProjectionHolder.isActive()) {
            RecordingService.stop(context)
            return
        }
        ProjectionHolder.updateState(RecordingState.REQUESTING_CONSENT)
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mgr.createScreenCaptureIntent()
        launcher.launch(intent)
    }

    fun stopSession(context: Context) {
        RecordingService.stop(context)
    }
}

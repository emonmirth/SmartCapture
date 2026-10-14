package com.eonmirth.smartcapture.recording

import android.media.projection.MediaProjection
import com.eonmirth.smartcapture.model.RecordingState
import kotlinx.coroutines.flow.StateFlow

/**
 * Compatibility facade over [RecorderController].
 * Existing UI/service code can keep calling ProjectionHolder;
 * all transitions go through the Phase 5 state machine.
 */
object ProjectionHolder {

    val state: StateFlow<RecordingState>
        get() = RecorderController.state

    fun currentProjection(): MediaProjection? = RecorderController.currentProjection()

    fun attach(p: MediaProjection) {
        RecorderController.attachProjection(p)
    }

    fun detach() {
        RecorderController.detachProjection()
    }

    /**
     * Prefer [RecorderController.tryTransitionSync] for new code.
     * This keeps legacy callers working with legality checks.
     */
    fun updateState(newState: RecordingState) {
        val applied = RecorderController.tryTransitionSync(newState)
        if (!applied) {
            if (newState == RecordingState.IDLE) {
                RecorderController.forceIdle("legacy updateState IDLE")
            }
        }
    }

    fun isActive(): Boolean = RecorderController.isSessionActive()
}

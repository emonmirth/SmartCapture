package com.eonmirth.smartcapture.recording

import android.media.projection.MediaProjection
import com.eonmirth.smartcapture.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide holder for the active MediaProjection and UI state.
 * Phase 3: projection only — no virtual display / encoder yet.
 *
 * Rules:
 * - One projection session per consent
 * - System onStop always wins
 * - stop() must be idempotent (handled by service)
 */
object ProjectionHolder {

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    @Volatile
    private var projection: MediaProjection? = null

    fun currentProjection(): MediaProjection? = projection

    fun attach(p: MediaProjection) {
        projection = p
    }

    fun detach() {
        projection = null
    }

    fun updateState(newState: RecordingState) {
        _state.value = newState
    }

    fun isActive(): Boolean {
        val s = _state.value
        return s == RecordingState.RECORDING ||
            s == RecordingState.PAUSED_BY_USER ||
            s == RecordingState.PAUSED_BY_SOURCE ||
            s == RecordingState.STARTING
    }
}

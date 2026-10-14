package com.eonmirth.smartcapture.recording

import android.media.projection.MediaProjection
import com.eonmirth.smartcapture.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialized recording state machine.
 * Phase 5: single controller for all transitions; idempotent stop; system onStop wins.
 */
object RecorderController {

    private const val TAG = "RecorderController"

    private val mutex = Mutex()
    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    @Volatile
    private var projection: MediaProjection? = null

    fun currentState(): RecordingState = _state.value

    fun currentProjection(): MediaProjection? = projection

    fun isSessionActive(): Boolean {
        val s = _state.value
        return s == RecordingState.RECORDING ||
            s == RecordingState.PAUSED_BY_USER ||
            s == RecordingState.PAUSED_BY_SOURCE ||
            s == RecordingState.STARTING ||
            s == RecordingState.REQUESTING_CONSENT ||
            s == RecordingState.FINALIZING
    }

    suspend fun transition(to: RecordingState): Boolean = mutex.withLock {
        applyTransition(to)
    }

    @Synchronized
    fun tryTransitionSync(to: RecordingState): Boolean = applyTransition(to)

    private fun applyTransition(to: RecordingState): Boolean {
        val from = _state.value
        if (from == to) return true
        if (!isLegal(from, to)) return false
        _state.value = to
        return true
    }

    @Synchronized
    fun forceIdle(reason: String = "force") {
        projection = null
        _state.value = RecordingState.IDLE
    }

    @Synchronized
    fun attachProjection(p: MediaProjection) {
        projection = p
    }

    @Synchronized
    fun detachProjection() {
        projection = null
    }

    fun requestStop(): StopRequestResult {
        val s = _state.value
        return when (s) {
            RecordingState.IDLE,
            RecordingState.SAVED,
            RecordingState.ERROR -> StopRequestResult.ALREADY_STOPPED
            RecordingState.FINALIZING -> StopRequestResult.ALREADY_STOPPING
            RecordingState.REQUESTING_CONSENT -> {
                tryTransitionSync(RecordingState.IDLE)
                StopRequestResult.CANCELLED_CONSENT
            }
            RecordingState.STARTING,
            RecordingState.RECORDING,
            RecordingState.PAUSED_BY_USER,
            RecordingState.PAUSED_BY_SOURCE -> {
                val ok = tryTransitionSync(RecordingState.FINALIZING)
                if (ok) StopRequestResult.STOPPING else StopRequestResult.REJECTED
            }
        }
    }

    fun requestPauseByUser(): Boolean = tryTransitionSync(RecordingState.PAUSED_BY_USER)

    fun requestResumeFromUserPause(): Boolean {
        if (_state.value != RecordingState.PAUSED_BY_USER) return false
        return tryTransitionSync(RecordingState.RECORDING)
    }

    fun requestPauseBySource(): Boolean = tryTransitionSync(RecordingState.PAUSED_BY_SOURCE)

    fun requestResumeFromSource(): Boolean {
        if (_state.value != RecordingState.PAUSED_BY_SOURCE) return false
        return tryTransitionSync(RecordingState.RECORDING)
    }

    private fun isLegal(from: RecordingState, to: RecordingState): Boolean {
        return when (from) {
            RecordingState.IDLE ->
                to == RecordingState.REQUESTING_CONSENT
            RecordingState.REQUESTING_CONSENT ->
                to == RecordingState.STARTING || to == RecordingState.IDLE
            RecordingState.STARTING ->
                to == RecordingState.RECORDING || to == RecordingState.ERROR || to == RecordingState.FINALIZING
            RecordingState.RECORDING ->
                to == RecordingState.PAUSED_BY_USER ||
                    to == RecordingState.PAUSED_BY_SOURCE ||
                    to == RecordingState.FINALIZING ||
                    to == RecordingState.ERROR
            RecordingState.PAUSED_BY_USER ->
                to == RecordingState.RECORDING ||
                    to == RecordingState.FINALIZING ||
                    to == RecordingState.ERROR
            RecordingState.PAUSED_BY_SOURCE ->
                to == RecordingState.RECORDING ||
                    to == RecordingState.FINALIZING ||
                    to == RecordingState.ERROR
            RecordingState.FINALIZING ->
                to == RecordingState.SAVED ||
                    to == RecordingState.ERROR ||
                    to == RecordingState.IDLE
            RecordingState.SAVED ->
                to == RecordingState.IDLE
            RecordingState.ERROR ->
                to == RecordingState.IDLE
        }
    }

    enum class StopRequestResult {
        STOPPING,
        ALREADY_STOPPING,
        ALREADY_STOPPED,
        CANCELLED_CONSENT,
        REJECTED
    }

    internal fun resetForTests() {
        projection = null
        _state.value = RecordingState.IDLE
    }
}

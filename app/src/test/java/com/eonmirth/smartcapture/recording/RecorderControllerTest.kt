package com.eonmirth.smartcapture.recording

import com.eonmirth.smartcapture.model.RecordingState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecorderControllerTest {

    @Before
    fun setUp() {
        RecorderController.resetForTests()
    }

    @After
    fun tearDown() {
        RecorderController.resetForTests()
    }

    @Test
    fun idle_to_requestingConsent_allowed() {
        assertTrue(RecorderController.tryTransitionSync(RecordingState.REQUESTING_CONSENT))
        assertEquals(RecordingState.REQUESTING_CONSENT, RecorderController.currentState())
    }

    @Test
    fun idle_to_recording_rejected() {
        assertFalse(RecorderController.tryTransitionSync(RecordingState.RECORDING))
        assertEquals(RecordingState.IDLE, RecorderController.currentState())
    }

    @Test
    fun happyPath_to_saved() {
        assertTrue(RecorderController.tryTransitionSync(RecordingState.REQUESTING_CONSENT))
        assertTrue(RecorderController.tryTransitionSync(RecordingState.STARTING))
        assertTrue(RecorderController.tryTransitionSync(RecordingState.RECORDING))
        assertTrue(RecorderController.tryTransitionSync(RecordingState.FINALIZING))
        assertTrue(RecorderController.tryTransitionSync(RecordingState.SAVED))
        assertTrue(RecorderController.tryTransitionSync(RecordingState.IDLE))
        assertEquals(RecordingState.IDLE, RecorderController.currentState())
    }

    @Test
    fun denyConsent_returnsIdle() {
        assertTrue(RecorderController.tryTransitionSync(RecordingState.REQUESTING_CONSENT))
        assertTrue(RecorderController.tryTransitionSync(RecordingState.IDLE))
        assertEquals(RecordingState.IDLE, RecorderController.currentState())
    }

    @Test
    fun pauseAndResume_user() {
        goToRecording()
        assertTrue(RecorderController.requestPauseByUser())
        assertEquals(RecordingState.PAUSED_BY_USER, RecorderController.currentState())
        assertTrue(RecorderController.requestResumeFromUserPause())
        assertEquals(RecordingState.RECORDING, RecorderController.currentState())
    }

    @Test
    fun pauseAndResume_source() {
        goToRecording()
        assertTrue(RecorderController.requestPauseBySource())
        assertEquals(RecordingState.PAUSED_BY_SOURCE, RecorderController.currentState())
        assertTrue(RecorderController.requestResumeFromSource())
        assertEquals(RecordingState.RECORDING, RecorderController.currentState())
    }

    @Test
    fun requestStop_fromRecording_goesFinalizing() {
        goToRecording()
        val result = RecorderController.requestStop()
        assertEquals(RecorderController.StopRequestResult.STOPPING, result)
        assertEquals(RecordingState.FINALIZING, RecorderController.currentState())
    }

    @Test
    fun requestStop_fromIdle_alreadyStopped() {
        val result = RecorderController.requestStop()
        assertEquals(RecorderController.StopRequestResult.ALREADY_STOPPED, result)
        assertEquals(RecordingState.IDLE, RecorderController.currentState())
    }

    @Test
    fun requestStop_idempotent_whileFinalizing() {
        goToRecording()
        assertEquals(RecorderController.StopRequestResult.STOPPING, RecorderController.requestStop())
        assertEquals(RecorderController.StopRequestResult.ALREADY_STOPPING, RecorderController.requestStop())
        assertEquals(RecordingState.FINALIZING, RecorderController.currentState())
    }

    @Test
    fun forceIdle_fromAnyState() {
        goToRecording()
        RecorderController.forceIdle("test")
        assertEquals(RecordingState.IDLE, RecorderController.currentState())
        assertFalse(RecorderController.isSessionActive())
    }

    @Test
    fun error_then_idle() {
        goToRecording()
        assertTrue(RecorderController.tryTransitionSync(RecordingState.ERROR))
        assertTrue(RecorderController.tryTransitionSync(RecordingState.IDLE))
        assertEquals(RecordingState.IDLE, RecorderController.currentState())
    }

    @Test
    fun illegal_jump_fromFinalizing_toRecording_rejected() {
        goToRecording()
        RecorderController.requestStop()
        assertFalse(RecorderController.tryTransitionSync(RecordingState.RECORDING))
        assertEquals(RecordingState.FINALIZING, RecorderController.currentState())
    }

    private fun goToRecording() {
        assertTrue(RecorderController.tryTransitionSync(RecordingState.REQUESTING_CONSENT))
        assertTrue(RecorderController.tryTransitionSync(RecordingState.STARTING))
        assertTrue(RecorderController.tryTransitionSync(RecordingState.RECORDING))
    }
}

package com.eonmirth.smartcapture.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.eonmirth.smartcapture.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Best-effort visibility signals for auto-pause.
 * Phase 6 — honest limits: screen on/off only as hard pause signal.
 * Does not track third-party app multi-window/PiP visibility.
 */
class VisibilityPolicy(
    private val context: Context,
    private val autoPauseEnabled: () -> Boolean,
    private val onAutoPause: () -> Unit,
    private val onAutoResume: () -> Unit
) {
    enum class Signal {
        SCREEN_OFF,
        SCREEN_ON,
        PROCESS_BACKGROUND,
        PROCESS_FOREGROUND
    }

    data class Snapshot(
        val screenOn: Boolean = true,
        val processForeground: Boolean = true,
        val lastSignal: Signal? = null,
        val notes: List<String> = emptyList()
    )

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private var registered = false
    private var pausedByPolicy = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> handle(Signal.SCREEN_OFF)
                Intent.ACTION_SCREEN_ON -> handle(Signal.SCREEN_ON)
            }
        }
    }

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            handle(Signal.PROCESS_FOREGROUND)
        }
        override fun onStop(owner: LifecycleOwner) {
            handle(Signal.PROCESS_BACKGROUND)
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(screenReceiver, filter)
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        _snapshot.value = Snapshot(
            screenOn = pm.isInteractive,
            processForeground = true,
            notes = listOf(
                "Auto-pause uses screen on/off and process lifecycle only.",
                "Captured app visibility is not tracked (would need restricted APIs)."
            )
        )
        registered = true
    }

    fun stop() {
        if (!registered) return
        try { context.unregisterReceiver(screenReceiver) } catch (_: Exception) { }
        try { ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver) } catch (_: Exception) { }
        registered = false
        pausedByPolicy = false
    }

    private fun handle(signal: Signal) {
        val prev = _snapshot.value
        val next = when (signal) {
            Signal.SCREEN_OFF -> prev.copy(screenOn = false, lastSignal = signal)
            Signal.SCREEN_ON -> prev.copy(screenOn = true, lastSignal = signal)
            Signal.PROCESS_BACKGROUND -> prev.copy(processForeground = false, lastSignal = signal)
            Signal.PROCESS_FOREGROUND -> prev.copy(processForeground = true, lastSignal = signal)
        }
        _snapshot.value = next
        if (!autoPauseEnabled()) return

        val state = RecorderController.currentState()
        val shouldPause = !next.screenOn
        // Process background alone does not pause (user may leave our UI while source stays fullscreen).

        if (shouldPause && state == RecordingState.RECORDING) {
            if (RecorderController.requestPauseBySource()) {
                pausedByPolicy = true
                onAutoPause()
            }
        } else if (!shouldPause && pausedByPolicy && state == RecordingState.PAUSED_BY_SOURCE) {
            if (RecorderController.requestResumeFromSource()) {
                pausedByPolicy = false
                onAutoResume()
            }
        }
    }

    companion object {
        val LIMITATION_NOTES = listOf(
            "Auto-pause triggers on screen off (reliable).",
            "We do not claim to detect when another app is hidden in multi-window/PiP.",
            "Accessibility-based tracking is not used."
        )
    }
}

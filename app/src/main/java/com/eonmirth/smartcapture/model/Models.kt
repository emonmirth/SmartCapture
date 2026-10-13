package com.eonmirth.smartcapture.model

/**
 * Core immutable domain models for SmartCapture.
 * Phase 2 — no recording logic yet; these drive UI and later capability probing.
 */

/** How the user wants to capture content. */
enum class CaptureMode {
    /** System-mediated single-app / window capture (Android 14 QPR2+ where available). */
    SINGLE_APP,
    /** Full-screen capture (fallback and older OS). */
    FULL_SCREEN
}

/** Quality / performance trade-off presets. */
enum class QualityPreset {
    /** Prefer lower resolution/FPS and impact. */
    BATTERY_SAVER,
    /** Practical default that most devices can sustain. */
    BALANCED,
    /** Higher resolution/FPS when the device probes support it. */
    HIGH_QUALITY
}

/** Audio capture options — only enabled when policy + device allow. */
enum class AudioOption {
    NONE,
    MICROPHONE,
    INTERNAL_PLAYBACK,
    MIC_AND_INTERNAL
}

/**
 * A concrete recording profile the user can select or save later.
 * Resolution and FPS are requested values; actual output depends on capability probe.
 */
data class QualityProfile(
    val preset: QualityPreset,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val bitrateMbps: Float,
    val displayName: String
) {
    companion object {
        val BatterySaver = QualityProfile(
            preset = QualityPreset.BATTERY_SAVER,
            width = 1280,
            height = 720,
            frameRate = 30,
            bitrateMbps = 4f,
            displayName = "Battery Saver (720p30)"
        )
        val Balanced = QualityProfile(
            preset = QualityPreset.BALANCED,
            width = 1920,
            height = 1080,
            frameRate = 30,
            bitrateMbps = 8f,
            displayName = "Balanced (1080p30)"
        )
        val HighQuality = QualityProfile(
            preset = QualityPreset.HIGH_QUALITY,
            width = 1920,
            height = 1080,
            frameRate = 60,
            bitrateMbps = 12f,
            displayName = "High Quality (1080p60)"
        )

        val defaults = listOf(BatterySaver, Balanced, HighQuality)
    }
}

/** Result of probing what this device/OS can actually do. */
data class DeviceCapability(
    val supportsSingleAppCapture: Boolean,
    val supportsInternalAudio: Boolean,
    val supportsMicrophone: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxFrameRate: Int,
    val hardwareEncoderAvailable: Boolean,
    val supportedProfiles: List<QualityProfile>,
    val notes: List<String> = emptyList()
)

/** High-level recording lifecycle states (full machine arrives in Phase 5). */
enum class RecordingState {
    IDLE,
    REQUESTING_CONSENT,
    STARTING,
    RECORDING,
    PAUSED_BY_USER,
    PAUSED_BY_SOURCE,
    FINALIZING,
    SAVED,
    ERROR
}

/** User preferences that survive process death (DataStore). */
data class UserPreferences(
    val captureMode: CaptureMode = CaptureMode.SINGLE_APP,
    val qualityPreset: QualityPreset = QualityPreset.BALANCED,
    val audioOption: AudioOption = AudioOption.NONE,
    val autoPauseEnabled: Boolean = true,
    val showFloatingControls: Boolean = false
)

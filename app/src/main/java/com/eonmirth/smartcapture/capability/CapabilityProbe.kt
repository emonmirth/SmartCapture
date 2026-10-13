package com.eonmirth.smartcapture.capability

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.eonmirth.smartcapture.model.DeviceCapability
import com.eonmirth.smartcapture.model.QualityProfile

/**
 * Probes what this device can actually support for screen recording.
 * Phase 2: codec + size + FPS probing only. No projection or encoding yet.
 *
 * Rules:
 * - Never assume 1080p60 / 120 FPS / internal audio everywhere.
 * - Prefer hardware AVC encoder when available.
 * - Report conservative max values; UI will disable unsupported presets.
 */
object CapabilityProbe {

    fun probe(context: Context): DeviceCapability {
        val notes = mutableListOf<String>()

        val supportsSingleApp = Build.VERSION.SDK_INT >= 34
        if (!supportsSingleApp) {
            notes.add("Single-app / window capture requires Android 14+. Full-screen will be used.")
        }

        val supportsMic = true
        val supportsInternalAudio = false
        notes.add("Internal audio support will be verified in a later phase; currently offered as unavailable.")

        val encoderInfo = findBestAvcEncoder()
        val hardwareEncoder = encoderInfo != null && !encoderInfo.name.contains("sw", ignoreCase = true)
            && !encoderInfo.name.contains("google", ignoreCase = true)

        val (maxW, maxH, maxFps) = if (encoderInfo != null) {
            probeEncoderLimits(encoderInfo)
        } else {
            notes.add("No suitable AVC encoder found; recording will be limited.")
            Triple(1280, 720, 30)
        }

        if (!hardwareEncoder) {
            notes.add("Hardware-accelerated AVC encoder not clearly available; performance may be limited.")
        }

        val allProfiles = QualityProfile.defaults
        val supported = allProfiles.filter { profile ->
            profile.width <= maxW && profile.height <= maxH && profile.frameRate <= maxFps
        }.ifEmpty {
            listOf(QualityProfile.BatterySaver)
        }

        val unsupported = allProfiles - supported.toSet()
        unsupported.forEach { p ->
            notes.add("${p.displayName} is not offered on this device (limit ~${maxW}x${maxH}@${maxFps}).")
        }

        return DeviceCapability(
            supportsSingleAppCapture = supportsSingleApp,
            supportsInternalAudio = supportsInternalAudio,
            supportsMicrophone = supportsMic,
            maxWidth = maxW,
            maxHeight = maxH,
            maxFrameRate = maxFps,
            hardwareEncoderAvailable = hardwareEncoder,
            supportedProfiles = supported,
            notes = notes
        )
    }

    private fun findBestAvcEncoder(): MediaCodecInfo? {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return list.codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
            .maxByOrNull { scoreEncoder(it) }
    }

    private fun scoreEncoder(info: MediaCodecInfo): Int {
        var score = 0
        val name = info.name.lowercase()
        if (!name.contains("sw") && !name.contains("google")) score += 10
        if (name.contains("c2") || name.contains("qcom") || name.contains("exynos") || name.contains("mtk")) score += 5
        return score
    }

    private fun probeEncoderLimits(info: MediaCodecInfo): Triple<Int, Int, Int> {
        return try {
            val caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val videoCaps = caps.videoCapabilities ?: return Triple(1280, 720, 30)

            val maxW = videoCaps.supportedWidths?.upper ?: 1920
            val maxH = videoCaps.supportedHeights?.upper ?: 1080
            val maxFps = try {
                videoCaps.getSupportedFrameRatesFor(maxW.coerceAtMost(1920), maxH.coerceAtMost(1080))
                    ?.upper?.toInt() ?: 30
            } catch (_: Exception) {
                30
            }

            Triple(
                maxW.coerceIn(720, 3840),
                maxH.coerceIn(720, 2160),
                maxFps.coerceIn(24, 60)
            )
        } catch (_: Exception) {
            Triple(1280, 720, 30)
        }
    }
}

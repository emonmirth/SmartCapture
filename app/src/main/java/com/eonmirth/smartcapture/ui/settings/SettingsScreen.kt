package com.eonmirth.smartcapture.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.modifier.modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.eonmirth.smartcapture.capability.CapabilityProbe
import com.eonmirth.smartcapture.data.PreferencesRepository
import com.eonmirth.smartcapture.model.AudioOption
import com.eonmirth.smartcapture.model.CaptureMode
import com.eonmirth.smartcapture.model.QualityPreset
import com.eonmirth.smartcapture.model.UserPreferences
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val repo = remember { PreferencesRepository(context) }
    val prefs by repo.preferences.collectAsState(initial = UserPreferences())
    val capability = remember { CapabilityProbe.probe(context) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        SettingsSection(title = "Capture source") {
            CaptureModeOption(
                label = "Single app",
                description = if (capability.supportsSingleAppCapture)
                    "System picker; only the selected app is intended to be captured"
                else
                    "Requires Android 14+. Not available on this device.",
                selected = prefs.captureMode == CaptureMode.SINGLE_APP,
                enabled = capability.supportsSingleAppCapture,
                onSelect = { scope.launch { repo.setCaptureMode(CaptureMode.SINGLE_APP) } }
            )
            CaptureModeOption(
                label = "Full screen",
                description = "Captures the entire screen (may include notifications and other apps)",
                selected = prefs.captureMode == CaptureMode.FULL_SCREEN,
                enabled = true,
                onSelect = { scope.launch { repo.setCaptureMode(CaptureMode.FULL_SCREEN) } }
            )
        }

        SettingsSection(title = "Quality") {
            QualityPreset.entries.forEach { preset ->
                val profile = capability.supportedProfiles.find { it.preset == preset }
                val supported = profile != null
                CaptureModeOption(
                    label = profile?.displayName ?: preset.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.uppercase() },
                    description = if (supported) "Supported on this device"
                    else "Not offered — device limit ~${capability.maxWidth}x${capability.maxHeight}@${capability.maxFrameRate}",
                    selected = prefs.qualityPreset == preset,
                    enabled = supported,
                    onSelect = {
                        if (supported) scope.launch { repo.setQualityPreset(preset) }
                    }
                )
            }
        }

        SettingsSection(title = "Audio") {
            AudioOption.entries.forEach { option ->
                val enabled = when (option) {
                    AudioOption.NONE -> true
                    AudioOption.MICROPHONE -> capability.supportsMicrophone
                    AudioOption.INTERNAL_PLAYBACK,
                    AudioOption.MIC_AND_INTERNAL -> capability.supportsInternalAudio
                }
                val desc = when (option) {
                    AudioOption.NONE -> "No audio track"
                    AudioOption.MICROPHONE -> "Record from microphone (permission required later)"
                    AudioOption.INTERNAL_PLAYBACK -> "Internal playback — not yet verified on this device"
                    AudioOption.MIC_AND_INTERNAL -> "Mic + internal — not yet verified"
                }
                CaptureModeOption(
                    label = option.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.uppercase() },
                    description = desc,
                    selected = prefs.audioOption == option,
                    enabled = enabled,
                    onSelect = {
                        if (enabled) scope.launch { repo.setAudioOption(option) }
                    }
                )
            }
        }

        SettingsSection(title = "Automation") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-pause when source hidden", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Only when visibility signal is reliable (tested later)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.autoPauseEnabled,
                    onCheckedChange = { scope.launch { repo.setAutoPauseEnabled(it) } }
                )
            }
        }

        if (capability.notes.isNotEmpty()) {
            SettingsSection(title = "Device notes") {
                capability.notes.forEach { note ->
                    Text(
                        text = "• $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Recording pipeline is not implemented yet (Phase 3+).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun CaptureModeOption(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

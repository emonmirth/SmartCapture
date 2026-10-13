package com.eonmirth.smartcapture.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier.modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eonmirth.smartcapture.LocalConsentLauncher
import com.eonmirth.smartcapture.R
import com.eonmirth.smartcapture.capability.CapabilityProbe
import com.eonmirth.smartcapture.data.PreferencesRepository
import com.eonmirth.smartcapture.model.CaptureMode
import com.eonmirth.smartcapture.model.QualityProfile
import com.eonmirth.smartcapture.model.RecordingState
import com.eonmirth.smartcapture.model.UserPreferences
import com.eonmirth.smartcapture.recording.ConsentHelper
import com.eonmirth.smartcapture.recording.ProjectionHolder

/**
 * Home / Record screen — Phase 3.
 * Record button requests MediaProjection consent and starts the foreground service.
 * No encoder yet — session is active only as projection + notification.
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repo = remember { PreferencesRepository(context) }
    val prefs by repo.preferences.collectAsState(initial = UserPreferences())
    val capability = remember { CapabilityProbe.probe(context) }
    val recordingState by ProjectionHolder.state.collectAsState()
    val consentLauncher = LocalConsentLauncher.current

    val profile = capability.supportedProfiles
        .find { it.preset == prefs.qualityPreset }
        ?: capability.supportedProfiles.firstOrNull()
        ?: QualityProfile.BatterySaver

    val modeLabel = when (prefs.captureMode) {
        CaptureMode.SINGLE_APP -> stringResource(R.string.single_app)
        CaptureMode.FULL_SCREEN -> stringResource(R.string.full_screen)
    }

    val isSessionActive = recordingState == RecordingState.RECORDING ||
        recordingState == RecordingState.STARTING ||
        recordingState == RecordingState.REQUESTING_CONSENT

    val statusText = when (recordingState) {
        RecordingState.IDLE -> "Ready • Consent required to start"
        RecordingState.REQUESTING_CONSENT -> "Waiting for system consent…"
        RecordingState.STARTING -> "Starting session…"
        RecordingState.RECORDING -> "Session active (no file yet — Phase 4 adds encoder)"
        RecordingState.PAUSED_BY_USER -> "Paused by user"
        RecordingState.PAUSED_BY_SOURCE -> "Paused (source hidden)"
        RecordingState.FINALIZING -> "Finalizing…"
        RecordingState.SAVED -> "Saved"
        RecordingState.ERROR -> "Error — session released"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.capture_source),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = modeLabel, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (prefs.captureMode == CaptureMode.SINGLE_APP && !capability.supportsSingleAppCapture)
                        "Not available on this OS — switch to Full screen in Settings"
                    else
                        "System picker confirms actual source",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Profile: ${profile.displayName}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Change in Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isSessionActive && recordingState != RecordingState.REQUESTING_CONSENT) {
            Button(
                onClick = { ConsentHelper.stopSession(context) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Stop capture session" }
            ) {
                Text("Stop", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Button(
                onClick = {
                    if (consentLauncher != null) {
                        ConsentHelper.requestConsent(context, consentLauncher)
                    }
                },
                enabled = consentLauncher != null && recordingState != RecordingState.REQUESTING_CONSENT,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Start capture — requests system consent" }
            ) {
                Text(
                    text = if (recordingState == RecordingState.REQUESTING_CONSENT)
                        "Waiting…"
                    else
                        stringResource(R.string.record_button),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.recent_recordings),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.empty_library_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

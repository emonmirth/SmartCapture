package com.eonmirth.smartcapture.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eonmirth.smartcapture.LocalConsentLauncher
import com.eonmirth.smartcapture.R
import com.eonmirth.smartcapture.capability.CapabilityProbe
import com.eonmirth.smartcapture.data.PreferencesRepository
import com.eonmirth.smartcapture.model.RecordingState
import com.eonmirth.smartcapture.recording.ConsentHelper
import com.eonmirth.smartcapture.recording.ProjectionHolder
import com.eonmirth.smartcapture.recording.RecorderController
import com.eonmirth.smartcapture.recording.RecordingService
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by ProjectionHolder.state.collectAsState()
    val prefsRepo = remember { PreferencesRepository(context) }
    val prefs by prefsRepo.preferences.collectAsState(
        initial = com.eonmirth.smartcapture.model.UserPreferences()
    )
    val capability = remember { CapabilityProbe.probe(context) }
    val consentLauncher = LocalConsentLauncher.current

    val statusText = when (state) {
        RecordingState.IDLE -> stringResource(R.string.status_ready)
        RecordingState.REQUESTING_CONSENT -> "Waiting for system permission…"
        RecordingState.STARTING -> "Starting…"
        RecordingState.RECORDING -> "Recording… (mic if enabled & permitted)"
        RecordingState.PAUSED_BY_USER -> "Paused"
        RecordingState.PAUSED_BY_SOURCE -> "Paused (screen off)"
        RecordingState.FINALIZING -> "Saving…"
        RecordingState.SAVED -> "Saved"
        RecordingState.ERROR -> "Error — try again"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("SmartCapture", style = MaterialTheme.typography.headlineMedium)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier = Modifier.padding(16.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Profile: ${prefs.qualityPreset.name} · ${prefs.captureMode.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        when (state) {
            RecordingState.IDLE, RecordingState.SAVED, RecordingState.ERROR -> {
                Button(
                    onClick = {
                        val launcher = consentLauncher ?: return@Button
                        if (RecorderController.tryTransitionSync(RecordingState.REQUESTING_CONSENT)) {
                            ConsentHelper.launchConsent(context, launcher)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(stringResource(R.string.record_button))
                }
            }
            RecordingState.RECORDING, RecordingState.PAUSED_BY_USER, RecordingState.PAUSED_BY_SOURCE -> {
                OutlinedButton(
                    onClick = { RecordingService.stop(context) },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Stop")
                }
            }
            else -> {
                Text(
                    "Please wait…",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Saved under Movies/SmartCapture — open Library tab",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (capability.notes.isNotEmpty()) {
            capability.notes.take(2).forEach { note ->
                Text(
                    "• $note",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

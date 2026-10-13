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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.modifier.modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eonmirth.smartcapture.R
import com.eonmirth.smartcapture.capability.CapabilityProbe
import com.eonmirth.smartcapture.data.PreferencesRepository
import com.eonmirth.smartcapture.model.CaptureMode
import com.eonmirth.smartcapture.model.QualityProfile
import com.eonmirth.smartcapture.model.UserPreferences

/**
 * Home / Record screen — Phase 2.
 * Shows live preference + capability info.
 * Record button remains disabled until Phase 3+.
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repo = remember { PreferencesRepository(context) }
    val prefs by repo.preferences.collectAsState(initial = UserPreferences())
    val capability = remember { CapabilityProbe.probe(context) }

    val profile = capability.supportedProfiles
        .find { it.preset == prefs.qualityPreset }
        ?: capability.supportedProfiles.firstOrNull()
        ?: QualityProfile.BatterySaver

    val modeLabel = when (prefs.captureMode) {
        CaptureMode.SINGLE_APP -> stringResource(R.string.single_app)
        CaptureMode.FULL_SCREEN -> stringResource(R.string.full_screen)
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
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.bodyLarge
                )
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

        Button(
            onClick = { /* Phase 3+ will wire this */ },
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics {
                    contentDescription = "Record button (disabled — recording not implemented yet)"
                }
        ) {
            Text(
                text = stringResource(R.string.record_button),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Text(
            text = stringResource(R.string.status_ready) + " • " + stringResource(R.string.status_not_implemented),
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

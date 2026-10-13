package com.eonmirth.smartcapture.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eonmirth.smartcapture.model.AudioOption
import com.eonmirth.smartcapture.model.CaptureMode
import com.eonmirth.smartcapture.model.QualityPreset
import com.eonmirth.smartcapture.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smartcapture_prefs")

/**
 * Local-only preferences via DataStore.
 * No accounts, no cloud. Phase 2.
 */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val CAPTURE_MODE = stringPreferencesKey("capture_mode")
        val QUALITY_PRESET = stringPreferencesKey("quality_preset")
        val AUDIO_OPTION = stringPreferencesKey("audio_option")
        val AUTO_PAUSE = booleanPreferencesKey("auto_pause")
        val FLOATING_CONTROLS = booleanPreferencesKey("floating_controls")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            captureMode = prefs[Keys.CAPTURE_MODE]?.let {
                runCatching { CaptureMode.valueOf(it) }.getOrDefault(CaptureMode.SINGLE_APP)
            } ?: CaptureMode.SINGLE_APP,
            qualityPreset = prefs[Keys.QUALITY_PRESET]?.let {
                runCatching { QualityPreset.valueOf(it) }.getOrDefault(QualityPreset.BALANCED)
            } ?: QualityPreset.BALANCED,
            audioOption = prefs[Keys.AUDIO_OPTION]?.let {
                runCatching { AudioOption.valueOf(it) }.getOrDefault(AudioOption.NONE)
            } ?: AudioOption.NONE,
            autoPauseEnabled = prefs[Keys.AUTO_PAUSE] ?: true,
            showFloatingControls = prefs[Keys.FLOATING_CONTROLS] ?: false
        )
    }

    suspend fun setCaptureMode(mode: CaptureMode) {
        context.dataStore.edit { it[Keys.CAPTURE_MODE] = mode.name }
    }

    suspend fun setQualityPreset(preset: QualityPreset) {
        context.dataStore.edit { it[Keys.QUALITY_PRESET] = preset.name }
    }

    suspend fun setAudioOption(option: AudioOption) {
        context.dataStore.edit { it[Keys.AUDIO_OPTION] = option.name }
    }

    suspend fun setAutoPauseEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PAUSE] = enabled }
    }

    suspend fun setShowFloatingControls(show: Boolean) {
        context.dataStore.edit { it[Keys.FLOATING_CONTROLS] = show }
    }
}

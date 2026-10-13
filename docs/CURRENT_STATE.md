# Current State — SmartCapture

**Last updated:** 2026-10-13  
**Phase:** 2 (Capability model + DataStore) complete in code

## Environment

- Host: Linux sandbox (no Android SDK / emulator)
- Builds and device tests must be run on a machine with Android Studio

## What exists now

### Phase 0–1
- Gradle project, Material 3 theme, bottom nav (Record / Library / Settings)
- Honest disabled Record button

### Phase 2 (new)
- **Models** (`model/Models.kt`): `CaptureMode`, `QualityPreset`, `AudioOption`, `QualityProfile`, `DeviceCapability`, `RecordingState`, `UserPreferences`
- **CapabilityProbe**: probes AVC encoder limits, single-app support (API 34+), hardware heuristic; never claims internal audio or 1080p60 blindly
- **PreferencesRepository**: DataStore-backed local prefs (capture mode, quality, audio, auto-pause, floating controls)
- **Settings screen**: live radio options; unsupported choices are disabled with explanations; device notes shown
- **Home screen**: reflects selected mode + supported profile from prefs/capability

### Explicit non-features
- No MediaProjection, no encoder session, no recording
- Internal audio marked unavailable until Phase 7 verifies it
- Record button still disabled

## Next task (Phase 3)

MediaProjection consent spike:
- Request consent, foreground-service type, callbacks, cleanup
- Test deny / cancel / system stop
- Still no encoder

## How to verify Phase 2 on device

1. Open project in Android Studio, sync, run.
2. Open Settings → change Capture source / Quality; unsupported options should be greyed out.
3. Return to Record tab → profile label should match selection.
4. Kill process and reopen → preferences should persist.

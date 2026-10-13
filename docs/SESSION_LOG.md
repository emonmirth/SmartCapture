# Session Log

## 2026-10-13 — Phase 0 & 1 scaffold

- Project structure, Gradle, Compose shell, disabled Record button
- Docs + .ai/RULES.md
- Pushed to https://github.com/emonmirth/SmartCapture

## 2026-10-13 — Phase 2 capability + preferences

- Added immutable models (CaptureMode, QualityProfile, DeviceCapability, UserPreferences, …)
- CapabilityProbe: MediaCodecList AVC probe, conservative max W/H/FPS, single-app gate on API 34+
- PreferencesRepository via DataStore
- Settings UI: selectable options, disabled unsupported presets with explanations, device notes
- Home UI: shows live mode + profile from prefs
- Still no recording / MediaProjection

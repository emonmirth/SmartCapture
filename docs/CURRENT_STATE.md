# Current State — SmartCapture

**Last updated:** 2026-10-13  
**Phase:** 0–1 (Skeleton + UI shell) completed in code

## Environment

- Host: Linux sandbox
- Java: OpenJDK 21
- Android SDK / emulator / sdkmanager: **not available in this environment**
- Therefore: source + Gradle files are ready, but `assembleDebug` and device tests must be run on a machine with Android Studio / full SDK.

## What exists now

### Docs
- `docs/PRODUCT_SPEC.md` — product vision & MVP scope
- `docs/ARCHITECTURE.md` — module layout & contracts
- `docs/TEST_PLAN.md` — acceptance criteria & phase tests
- `docs/CURRENT_STATE.md` — this file
- `.ai/RULES.md` — AI engineering rules from master pack

### Build system
- Root `settings.gradle.kts`, `build.gradle.kts`
- `gradle/libs.versions.toml` (AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10.01)
- `app/build.gradle.kts` (minSdk 26, target/compile 35, Compose enabled)
- Gradle wrapper properties (8.9)

### Application code (Phase 0–1)
- `SmartCaptureApp` + `MainActivity`
- Material 3 theme (Pixel-inspired blue, dynamic color support)
- Bottom navigation: Record / Library / Settings
- `HomeScreen` — capture source card, profile card, large Record button (**disabled**), status text, recent section
- `LibraryScreen` — empty state
- `SettingsScreen` — placeholder groups note
- Strings, basic theme resources
- Manifest with no recording permissions yet (added only when features land)

### Explicit non-features (honest UX)
- Record button is disabled and labeled as not implemented
- No MediaProjection, no encoder, no fake telemetry
- No mock recordings

## Blockers / Limitations

1. Cannot compile or run APK inside this sandbox (no Android SDK).
2. Launcher icons are temporary `@android:drawable` fallbacks — replace before release.
3. Multi-module structure is prepared as directories only; currently single `:app` module for fastest first build.

## Next task (Phase 2)

Create immutable models (`CaptureMode`, `RecordingState`, `QualityProfile`, `Capability`, etc.) and a simple capability probe stub. Persist preferences with DataStore. Still no recording.

After that: Phase 3 MediaProjection consent spike.

## How to build on a real machine

1. Open the `smartcapture` folder in Android Studio (Ladybug / recent).
2. Let it sync Gradle.
3. Add proper adaptive launcher icons.
4. Run on emulator or device (API 26+).
5. Confirm navigation and disabled Record button.

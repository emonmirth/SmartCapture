# SmartCapture

Native Android screen recorder for mobile gamers and content creators.

**Status:** Phase 0–1 complete (UI shell). Recording pipeline not yet implemented.

## Quick start

1. Open this folder in **Android Studio** (recent stable).
2. Sync Gradle.
3. Replace temporary launcher icons if desired.
4. Run on an emulator or device (API 26+).

You should see a three-tab app (Record / Library / Settings) with a disabled Record button. This is intentional.

## Project rules

Read `.ai/RULES.md` and everything under `docs/` before making changes.

Work **one phase at a time**. Never claim a feature works without a verified build/test.

## Phase roadmap (summary)

| Phase | Focus |
|-------|--------|
| 0–1   | Audit, skeleton, Material 3 navigation shell (done) |
| 2     | Capability model + DataStore preferences |
| 3     | MediaProjection consent + foreground service spike |
| 4     | MediaCodec + MediaMuxer → playable MP4 |
| 5     | Full state machine + unit tests |
| 6     | App-visibility auto-pause/resume (where reliable) |
| 7+    | Audio, notification/overlay, library, performance, release |

See `docs/PRODUCT_SPEC.md` and the original Master Pack for full details.

## Stack

- Kotlin, Jetpack Compose, Material 3
- MediaProjection / MediaCodec / MediaMuxer (later phases)
- DataStore, Coroutines/Flow
- minSdk 26, targetSdk 35

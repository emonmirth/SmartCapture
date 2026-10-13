# SmartCapture Architecture

## Module layout

```
smartcapture/
├── app/                          # Application, navigation, DI wiring
├── core/
│   ├── model/                    # CaptureMode, RecordingState, Profile, Capability, errors
│   ├── designsystem/             # Material 3 theme, typography, reusable components
│   └── common/                   # Shared utilities
├── feature/
│   ├── home/                     # Capture source, profile, Record button, status
│   ├── library/                  # MediaStore list, thumbnails, share/delete
│   ├── settings/                 # Quality, audio, automation, privacy
│   └── onboarding/               # Permission explanation, privacy disclosure
├── data/
│   ├── preferences/              # DataStore for profiles & settings
│   └── media/                    # MediaStore helpers
├── recording/
│   ├── api/                      # RecorderController + StateFlow UI state
│   ├── projection/               # MediaProjection consent, virtual display, callbacks
│   ├── encoder/                  # MediaCodec probing & encoding
│   ├── audio/                    # Playback capture + mic (where allowed)
│   ├── muxer/                    # MediaMuxer + MP4 finalization
│   └── policy/                   # State machine, visibility debounce, auto-pause/resume
└── diagnostics/                  # Non-sensitive logs, error codes
```

## Key contracts

- `RecorderController` exposes immutable `StateFlow<RecordingUiState>`
- All media work off main thread
- One projection session per granted consent
- `stop()` is idempotent; resources released on every exit path
- Visibility events debounced; manual actions override automation
- System `MediaProjection.Callback.onStop` always wins

## State machine

```
IDLE
 → REQUESTING_CONSENT
 → STARTING
 → RECORDING
 → PAUSED_BY_USER | PAUSED_BY_SOURCE
 → FINALIZING
 → SAVED | ERROR
```

## Technology choices

- Kotlin, Jetpack Compose, Material 3
- Coroutines + Flow
- DataStore
- MediaProjection + MediaCodec + MediaMuxer
- Hilt (or manual DI if keeping dependencies minimal for MVP)

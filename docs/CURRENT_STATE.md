# Current State — SmartCapture

**Last updated:** 2026-10-13  
**Phase:** 3 (MediaProjection consent spike) complete in code

## Environment

- Host: Linux sandbox (no Android SDK / emulator)
- Build and device tests required on a machine with Android Studio

## What exists now

### Phase 0–2
- UI shell, models, CapabilityProbe, DataStore preferences

### Phase 3 (new)
- **Manifest**: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `POST_NOTIFICATIONS`
- **RecordingService**: foreground service with `mediaProjection` type, ongoing notification + Stop action
- **ProjectionHolder**: process-wide `StateFlow<RecordingState>` + active `MediaProjection` reference
- **ConsentHelper**: system consent dialog via Activity Result API; grant → start service; deny/cancel → IDLE
- **HomeScreen**: Record requests consent; Stop ends session; status reflects live state
- **MainActivity**: registers consent launcher + notification permission (API 33+)

### Explicit non-features (still)
- No VirtualDisplay, no MediaCodec, no MP4 file
- Session active means projection + notification only
- Auto-pause / visibility policy not implemented (Phase 6)
- Audio not implemented (Phase 7)

### Lifecycle guarantees in this phase
- New consent for every session (no token reuse)
- `MediaProjection.Callback.onStop` releases resources and returns to IDLE
- Stop from notification or UI is idempotent
- Service uses `START_NOT_STICKY` and clears notification on stop

## How to verify on device

1. Run app → tap Record → system consent dialog appears
2. Deny → status returns to Ready
3. Grant → foreground notification appears; status = Session active
4. Tap Stop (UI or notification) → notification gone; status = Ready
5. Grant again, then system stop / lock → onStop should clean up
6. Confirm no MP4 is written (expected)

## Next task (Phase 4)

Connect capture surface to MediaCodec + MediaMuxer → playable MP4.

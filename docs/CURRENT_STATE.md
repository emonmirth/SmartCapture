# Current State — SmartCapture

**Last updated:** 2026-10-13  
**Phase:** 4 (Encoder + MP4) complete in code

## Environment

- Host: Linux sandbox (no Android SDK / emulator)
- Build and device tests required on a machine with Android Studio

## What exists now

### Phase 0–3
- UI, prefs, capability probe, MediaProjection consent, FGS

### Phase 4 (new)
- **VideoEncoder**: Surface-input H.264 (MediaCodec), MediaMuxer → MP4
- **RecordingOutput**: temp file → MediaStore Movies/SmartCapture
- **RecordingService**: VirtualDisplay + encoder; stop finalizes and publishes
- Home status: Recording / Saving / Saved
- Still **no audio** (Phase 7)

### How to verify on device

1. Record → grant consent → notification Recording…
2. Use phone 10–30s → Stop
3. Status → Saving → Saved
4. Gallery / Files → Movies/SmartCapture → play MP4 (no audio expected)
5. Deny consent → no file

## Next (Phase 5)

State machine hardening + unit tests.

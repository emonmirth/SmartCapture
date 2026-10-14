# Current State — SmartCapture

**Last updated:** 2026-10-14  
**Phase:** 5 (State machine) complete in code

## Environment

- Host: Linux sandbox (no Android SDK)
- Unit tests: `./gradlew test` on a machine with SDK/JDK
- Device APK: `./gradlew assembleDebug`

## What exists now

### Phase 0–4
- UI, prefs, capability probe, MediaProjection consent, FGS
- VirtualDisplay + MediaCodec + MediaMuxer → MP4 (video only)

### Phase 5 (new)
- **RecorderController**: serialized legal transitions, idempotent `requestStop()`, forceIdle for system onStop
- **ProjectionHolder**: facade over RecorderController (backward compatible)
- **ConsentHelper / RecordingService**: stop path uses `requestStop()`
- **Unit tests**: `RecorderControllerTest` (happy path, deny, pause/resume, idempotent stop, illegal jumps)

### State machine rules
```
IDLE → REQUESTING_CONSENT → STARTING → RECORDING
  ⇄ PAUSED_BY_USER | PAUSED_BY_SOURCE
  → FINALIZING → SAVED → IDLE
  → ERROR → IDLE
```
- Illegal transitions rejected
- System revoke uses forceIdle
- requestStop is idempotent

## Verify

1. `./gradlew test` — all RecorderControllerTest pass
2. Device: Record → Stop twice quickly should not crash
3. Deny consent → IDLE
4. MP4 still saves as in Phase 4

## Next (Phase 6)

App visibility policy / auto-pause when signal reliable; honest fallback otherwise.

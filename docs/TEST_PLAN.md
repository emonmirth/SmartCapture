# SmartCapture Test Plan

## Device matrix (minimum)

- Android 14 QPR2+ device for single-app / window capture
- Older supported Android version for full-screen fallback
- Low/mid-range + high-end devices (at least two OEM families if possible)
- Portrait + landscape, rotation, lock screen, notification shade, low storage, process death, permission revocation

## Core acceptance criteria

- [ ] Every new capture requests valid user consent
- [ ] Single-app mode does not intentionally record unrelated UI on supported OS
- [ ] Auto-pause/resume only when visibility signal is reliable and session is still valid
- [ ] Notification and UI state stay synchronized
- [ ] Produced MP4 plays with correct duration and A/V sync
- [ ] All resources (projection, display, surface, codec, audio, muxer) released on stop/error/cancel
- [ ] Low-storage and unsupported-codec errors are clear and non-crashing
- [ ] No fabricated FPS / temperature / battery / dropped-frame numbers
- [ ] Measured comparison of game performance with vs without recorder

## Phase-specific tests

### Phase 0–1 (skeleton + UI)
- App launches, navigates between Record / Library / Settings
- Buttons disabled or clearly non-functional until wired
- UI tests for navigation and empty states
- Debug APK builds

### Phase 3 (MediaProjection)
- Consent grant / deny / cancel
- System stop, lock screen, repeated sessions
- Proper foreground-service notification

### Phase 4–5 (Encoder + State machine)
- Playable MP4 from short and longer captures
- Unit tests for state transitions and idempotent stop
- Resource cleanup verified (no leaks on rapid start/stop)

### Phase 6 (Visibility policy)
- Real-device verification of visibility callbacks
- Debounce prevents pause/resume loops
- Fallback messaging when signal is unreliable

### Later phases
- Audio sync over 20+ minutes
- Overlay permission + OEM restrictions
- Library operations (rename/delete/share) and interrupted sessions
- Performance / thermal measurements under controlled conditions

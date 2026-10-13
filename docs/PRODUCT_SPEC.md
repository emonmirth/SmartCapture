# SmartCapture Product Specification

**Working title:** SmartCapture  
**Version:** 1.0  
**Date:** 13 October 2026  
**Target:** Mobile Gamers + YouTubers + Content Creators  
**Priority:** Smart Balance  
**Design:** Pixel-inspired Material 3

## Vision

SmartCapture is a gamer- and creator-focused screen recorder that prioritizes:

- Fast start with saved profiles and a prominent Record button
- Privacy via system-mediated MediaProjection (user consent required)
- Smart Balance of quality, frame pacing, battery and thermal impact
- Creator-ready local library, share and (later) basic trim
- Honest UX — only measured telemetry and real device support

## MVP (P0) Scope

- Capture modes: single-app/window (Android 14 QPR2+) and full-screen
- Explicit state machine with proper consent and resource cleanup
- MP4 via MediaCodec + MediaMuxer
- Quality presets: Balanced (default), Battery Saver, High Quality
- Optional internal audio + mic where policy allows
- Foreground notification with elapsed time + Stop
- Local library (thumbnail, duration, resolution, size, rename, delete, share)
- Accessible Material 3 UI, light/dark

## Out of scope for MVP

- Facecam, live streaming, AI tools, cloud, accounts, advanced editor
- Accessibility hacks, hidden APIs, covert capture
- Guaranteed auto-resume on every device
- Universal 90/120 FPS or zero-lag claims

## Platform constraints (must respect)

- MediaProjection consent for every new session
- Visibility callbacks do not guarantee background app keeps rendering
- No root / hidden APIs / Accessibility for foreground detection
- Proper foreground-service type for mediaProjection
- Probe codec capabilities; never assume hardware encoding everywhere

See full master pack for detailed feature matrix, architecture and phase plan.

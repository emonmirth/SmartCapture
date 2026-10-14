# Current State — SmartCapture

**Last updated:** 2026-10-14  
**Phase:** 6 (Visibility policy) complete in code

## What exists

### Phase 0–5
- UI, prefs, capability, consent, FGS, encoder→MP4, state machine + unit tests

### Phase 6 (new)
- **VisibilityPolicy**: screen on/off + process lifecycle
- Auto-pause → PAUSED_BY_SOURCE only on **screen off** when Settings toggle is on
- Auto-resume when screen turns on
- Honest non-claims: no multi-window/PiP/source-app visibility without restricted APIs
- Settings copy updated; wired into RecordingService

## Limits
- Cannot know if the *captured* third-party app is hidden
- Process background alone does not pause
- Accessibility is not used

## Verify
1. Enable Auto-pause → Record → screen off → Paused
2. Screen on → resumes
3. Disable auto-pause → screen off does not pause

## Next (Phase 7)
Audio: mic first; internal only where proven.

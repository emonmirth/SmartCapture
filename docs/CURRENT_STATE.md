# Current State — SmartCapture

**Phase:** 7 (Audio — microphone) complete in code

## Phase 7
- CaptureMuxer + MicAudioEncoder (AAC)
- VideoEncoder → shared muxer
- RECORD_AUDIO; mic when Settings + permission
- Internal audio not captured (honest UI)

## Verify
1. Grant mic → Settings Microphone → Record → MP4 has audio
2. Deny mic → video-only still works

## Next: Phase 8 notification / floating controls

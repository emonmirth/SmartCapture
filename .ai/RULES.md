# SmartCapture AI Engineering Rules

1. Read .ai/RULES.md and docs/* before editing.
2. Work on one phase/task at a time.
3. Never claim a feature works unless code exists and a test/build verifies it.
4. No mock data, placeholder buttons or fake telemetry presented as real.
5. Public Android SDK APIs only. No root, hidden APIs, Accessibility hacks or covert capture.
6. Respect MediaProjection consent, foreground-service rules and system indicators.
7. Release every projection/encoder/audio/muxer resource on all stop/error/cancel paths.
8. Expose immutable UI state through StateFlow; keep blocking media work off main thread.
9. Avoid busy loops, per-frame bitmap copies, unbounded queues and needless polling.
10. Probe capabilities; never assume 1080p60, 120 FPS, internal audio or hardware encoding everywhere.
11. No zero-lag/low-battery claims without measured tests.
12. Add unit tests for state machine/policy and manual/instrumentation tests for device-specific behavior.
13. After each change: tests, lint where available, debug APK build, docs/CURRENT_STATE.md update.
14. If API/device limitations block a feature, report evidence and alternatives; no misleading workaround.
15. Never delete recordings automatically.
16. Keep dependencies minimal; explain every new dependency.
17. Ask before analytics, network, accounts, cloud, ads or unrelated permissions.
18. Keep UI accessible, Material 3 and lightweight.
19. Never commit secrets/signing keys.
20. Final report: changed files, implementation, tests, build result, limitations, next task.

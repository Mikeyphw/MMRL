# Unified Module Browser · Phase 17

## Final integration / regression seal

Phase 17 seals the unified browser stack added in Phases 10 through 16. It does not redesign UI again and does not add mutating module operations. Instead it introduces a pure regression-seal model that checks the canonical rows, controls, Problems center, action planner, and presentation policy together.

## Added

- `UnifiedModuleBrowserRegressionSeal.kt`
- `UnifiedModuleBrowserRegressionSealTest`
- `UnifiedModuleBrowserFinalSealContractTest`

## What the seal validates

- Source coverage across installed, repo, GitHub, LSPosed, local, and rescue rows.
- View coverage for Installed, Repo, Updates, Scopes, Problems, and GitHub Sources.
- Problem/action routing through the Phase 13 problem model and Phase 14/15 action planner.
- Destructive action guardrails remain closed.
- Compact, Comfortable, and Diagnostic density behavior stays deterministic.
- Fielded search prefixes stay aligned with the presentation help text.

## Safety boundary

No install, remove, enable, disable, or scope-write execution is added here. Phase 17 only proves that the existing safe action layer remains non-destructive and that future mutating flows must be added as explicit confirmed flows.

## Why this is useful

The unified browser now combines data from many lanes: root-installed modules, repository entries, saved GitHub sources, LSPosed repository/installed APK evidence, and local module ZIPs. The regression seal gives future phases a small cockpit gauge: if someone adds a new source, badge, action, or density behavior, tests can catch accidental gaps before the UI quietly loses a lane.

## Recommended validation

```bash
:app:testOfficialDebugUnitTest
:app:lintOfficialDebug
```

Expected result after applying Phase 17: tests and diagnostics remain clean.

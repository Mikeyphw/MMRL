# Unified Module Browser · Release Notes

## Ship summary

The unified module browser arc combines root-installed modules, repository rows, saved GitHub module sources, LSPosed installed/repository evidence, and local/manual module evidence into one canonical browser model.

The release is intentionally safe-first. No install, remove, enable, disable, or LSPosed scope-write execution is added by the unified browser. Mutating operations still need explicit confirmed flows before they can become executable.

## Phase index

- `UNIFIED_MODULE_BROWSER_PHASE10.md` · Canonical module model, aliases, source/mode/state/badge vocabulary, and match explanations.
- `UNIFIED_MODULE_BROWSER_PHASE11.md` · View buckets, filter/sort/search controls, fielded search, density state, and browser stats.
- `UNIFIED_MODULE_BROWSER_PHASE12.md` · Lightweight UI wiring in the existing Modules screen.
- `UNIFIED_MODULE_BROWSER_PHASE13.md` · Problems center, evidence, problem actions, and digest cards.
- `UNIFIED_MODULE_BROWSER_PHASE14.md` · Safe action planner and clickable problem/row actions.
- `UNIFIED_MODULE_BROWSER_PHASE15.md` · Action-result copy, destinations, tones, guidance, and inline result cards.
- `UNIFIED_MODULE_BROWSER_PHASE16.md` · Presentation policy, badge hierarchy, empty states, and density polish.
- `UNIFIED_MODULE_BROWSER_PHASE17.md` · Cross-source regression seal.
- `UNIFIED_MODULE_BROWSER_PHASE18.md` · Release seal, final docs, and safety-boundary cleanup.
- `UNIFIED_MODULE_BROWSER_RELEASE_NOTES.md` · Consolidated ship note and phase index.

## Supported lanes

- Installed root modules.
- Repository modules.
- Saved GitHub sources in release, nightly, manual, and mixed modes.
- LSPosed repository modules.
- Installed LSPosed APK modules.
- Local/manual module evidence.

## Supported views

- Installed.
- Repo.
- Updates.
- Scopes.
- Problems.
- GitHub Sources.

## Safe actions

- Open/narrow to an installed module row.
- Copy source URL.
- Copy unified evidence.
- Refresh provider/repository/module evidence.
- Run safe diagnostics refresh.
- Guide users to GitHub source rules, LSPosed manager/scope review, module review, and Debug Workbench surfaces.

## Release validation

Use the same targeted Android gate as the prior phases:

```bash
:app:testOfficialDebugUnitTest
:app:lintOfficialDebug
```

Expected release condition: unit tests pass and diagnostics stay at 0 warnings, 0 errors.

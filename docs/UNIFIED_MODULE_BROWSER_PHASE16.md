# Unified Module Browser Phase 16 - UX polish

Phase 16 turns the Phase 10-15 unified browser foundation into a more coherent cockpit without changing destructive behavior or replacing the full installed-module UI.

## Scope

- Keep installed modules on the existing mature card stack.
- Keep unified non-installed views read-only except for the safe Phase 14/15 actions.
- Add a pure presentation policy layer so Compose is not responsible for badge order, density limits, empty-state copy, or diagnostic line selection.
- Improve the unified header summary, stat pills, search field help, and filter-empty recovery.
- Make compact, comfortable, and diagnostic density modes more distinct.
- Promote error/warning/update/provider badges before lower-priority informational badges.
- Preserve explicit guardrails: no install, remove, enable, disable, or scope-write action is introduced here.

## New presentation model

`UnifiedModuleBrowserPresentation` derives:

- `UnifiedBrowserChromePresentation` for header title, row counts, problem/update hints, stat pills, and search help.
- `UnifiedBrowserEmptyPresentation` for filter-aware empty states and recovery suggestions.
- `UnifiedModuleCardPresentation` for metadata pills, badge hierarchy, hidden badge count, action limits, and diagnostic lines.
- `UnifiedBadgePresentation` with high, medium, and low emphasis.

This keeps UX decisions deterministic and testable with regular unit tests instead of relying on screenshot tests.

## UI wiring

`UnifiedModuleBrowserPanel` now uses the presentation model for:

- header summary and stats;
- empty-state copy and clear-filter affordance;
- card metadata pills;
- badge strip ordering and overflow;
- compact versus diagnostic action limits;
- diagnostic lines.

## Contracts

Added:

- `UnifiedModuleBrowserPresentationTest`

Updated:

- `UnifiedModuleBrowserUiContractTest`

The contracts assert that presentation policy stays centralized and the screen still exposes unified views, filters, result cards, problem cards, safe actions, and clear-filter recovery.

## Phase 17 handoff

Phase 17 should be the final integration/regression seal:

- remove stale duplicate helper code;
- add regression coverage across installed/repo/GitHub/LSPosed/rescue/local paths;
- ensure diagnostics remain at 0 warnings and 0 errors;
- tighten handoff docs and final apply notes.

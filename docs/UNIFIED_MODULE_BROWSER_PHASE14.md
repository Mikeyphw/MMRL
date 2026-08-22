# Unified Module Browser · Phase 14 Actions Wiring

Phase 14 turns the Phase 13 descriptive action chips into a safe action layer. The work is still intentionally incremental: it wires useful local actions and guided messages without adding destructive module operations or replacing the Modules UI shell.

## What changed

- Added `UnifiedModuleBrowserActions.kt` with:
  - `UnifiedModuleBrowserActionKind`
  - `UnifiedModuleBrowserAction`
  - `UnifiedModuleBrowserActionResult`
  - `UnifiedModuleBrowserActionPlanner`
- Problem cards now render clickable action chips instead of inert labels.
- Unified non-installed cards now expose row-level actions from the same planner.
- `ModulesViewModel` now handles unified browser actions through `runUnifiedBrowserAction`.
- Safe executable actions:
  - copy problem evidence
  - copy unified row evidence
  - copy source URL
  - refresh provider/module signals
  - refresh local repository evidence
  - run a safe diagnostics refresh
  - jump back to Installed view and search for a module id
- Guided actions remain non-mutating messages:
  - edit saved GitHub source rules
  - open manager
  - review LSPosed scope
  - review module state
  - suggested fix

## Safety contract

This phase does not install, uninstall, enable, disable, repair, or apply LSPosed scope changes. The action model carries a `destructive` flag and the ViewModel rejects destructive actions without a confirmation flow.

The current executable actions are deliberately tiny switches, not chainsaws:

- clipboard writes for evidence and URLs
- local provider/repository refreshes
- existing in-screen search/view routing
- snackbar guidance for flows that need a dedicated screen or confirmation

## UI behavior

In the Problems view, action chips now call the ViewModel. On other unified views, canonical cards can expose actions such as `Copy source URL`, `Edit source rules`, `Refresh provider`, `Review scope`, or `Review module` depending on row evidence.

Installed modules still keep the mature existing installed-module cards and menus. Phase 14 only makes the unified model's action vocabulary visible and testable.

## Contracts added

- `UnifiedModuleBrowserActionPlannerTest`
  - problem action to clipboard evidence mapping
  - GitHub source row action generation
  - safe refresh/probe results
- `UnifiedModuleBrowserActionWiringContractTest`
  - non-destructive action model vocabulary
  - ViewModel clipboard/refresh/routing guards
  - UI action chip wiring

## Next phase notes

Phase 15 can add deeper navigation and action result polish:

- route `Edit source rules` directly into the existing source dialog where a local module match exists
- route `Review scope` into the LSPosed scope detail/editor sheet
- route manager actions through the LSPosed manager opener
- export unified action evidence into support bundles

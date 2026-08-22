# Unified Module Browser · Phase 13 Problems Layer

Phase 13 adds the problem and health layer for the unified module browser. It does not perform destructive repairs, write LSPosed scope databases, or replace the whole app shell. It converts existing canonical badges and future repository/debug signals into stable, action-ready problem cards.

## What changed

- Added `UnifiedModuleProblems.kt` with:
  - `UnifiedModuleProblemReport`
  - `UnifiedModuleProblem`
  - `UnifiedModuleProblemKind`
  - `UnifiedProblemActionKind`
  - `UnifiedProblemSignal`
  - `UnifiedModuleProblemCenter`
- Added problem categories for the roadmap diagnostics:
  - primary repo 403
  - backup repo fallback
  - malformed entries skipped
  - cache fallback
  - GitHub artifact expired
  - GitHub token required
  - GitHub regex mismatch
  - manager unavailable
  - provider bridge available
  - scope DB unavailable
  - installed but not in repo
  - alias match only
  - disabled module
  - failed or pending update
  - module review
- Added action vocabulary for each problem:
  - open module
  - run probe
  - copy evidence
  - suggested fix
  - edit GitHub source
  - refresh provider
  - open manager
  - review scope
  - review rescue
  - check repository
- `ModulesViewModel` now exposes:
  - `unifiedProblemReport`
  - `filteredUnifiedProblemReport`
- The unified Problems view now renders problem report cards instead of plain module cards.
- Search learned `problem:` / `issue:` and `severity:` field prefixes.

## How problems are generated now

Phase 13 derives problems from the unified rows that already exist:

- warning/error badges become problem cards
- installed modules without repository or saved-source evidence become update-source problems
- alias-only matches become review notes
- disabled modules become warning cards
- update-pending modules become update-watch cards
- unavailable or limited providers become manager/provider cards
- LSPosed rows without scope evidence become scope DB cards

The `UnifiedProblemSignal` type is intentionally present even before every runtime producer is wired. Later repo refreshes, Debug Workbench probes, support bundle exports, GitHub artifact resolver failures, and LSPosed scope probes can feed the same problem vocabulary without changing the UI contract.

## UI behavior

When the unified browser view is `Problems`, the screen now shows:

- a digest card with error/warning/note/action counts
- one problem card per actionable signal
- evidence lines
- action chips that describe the next safe operation

Actions are intentionally descriptive in this phase. Later phases can bind them to navigation, probes, clipboard, source editors, and provider refresh calls.

## Contracts added

- `UnifiedModuleProblemCenterTest`
  - repository-source problem generation
  - alias review notes
  - external repo/GitHub signal vocabulary
  - problem search filtering
- `UnifiedModuleProblemWiringContractTest`
  - ViewModel report exposure
  - roadmap problem/action vocabulary
- `UnifiedModuleBrowserUiContractTest` was extended to require problem report collection and problem cards.

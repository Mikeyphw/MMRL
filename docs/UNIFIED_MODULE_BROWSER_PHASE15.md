# Unified Module Browser · Phase 15

Phase 15 polishes safe action execution for the unified module browser. Phase 14 made problem and row chips clickable; Phase 15 gives every result a shared outcome model so snackbars, inline result cards, and future navigation/deep-link work all speak the same language.

## What changed

- Added `UnifiedModuleBrowserActionTone` for success, info, warning, and blocked outcomes.
- Added `UnifiedModuleBrowserActionDestination` for guided destinations such as Installed modules, GitHub source rules, LSPosed manager, rescue controls, Debug Workbench, and repository refresh.
- Extended `UnifiedModuleBrowserActionResult` with destination, follow-up text, result tone, guidance checks, and a user-facing combined message.
- Added `UnifiedModuleBrowserActionPlanner.blockedResult` so rejected write-like actions produce the same explicit feedback everywhere.
- Added `UnifiedModuleBrowserActionPlanner.resultSummary` for compact chips in the UI.
- Added `ModulesViewModel.unifiedBrowserActionResult` so the latest action result can be shown inline in addition to the existing snackbar path.
- Extracted action execution routing into `executeUnifiedBrowserAction` to keep planning, execution, clipboard, refresh, and navigation behavior easier to test.
- Added `UnifiedModuleActionResultCard` beneath the unified browser header.

## Safe execution rules

Phase 15 does not add install, remove, enable, disable, LSPosed scope write, or rescue restore actions. Existing safe actions remain limited to:

- copy evidence
- copy source URL
- refresh local repository evidence
- refresh provider, LSPosed, module, and rescue signals
- run safe diagnostics refresh
- jump to Installed view and narrow search by module id
- provide guided next-step destinations for GitHub rules, LSPosed manager, scope review, and rescue review

Any disabled or destructive action still resolves through the blocked-result path before execution.

## UI behavior

The Modules screen now collects the latest unified browser action result. The Modules list renders a compact inline card below the unified browser header with:

- primary result message
- optional follow-up guidance
- tone chip
- destination / clipboard / guided summary chips

Snackbars still fire through the existing `ashMessages` stream, but their text is now derived from `UnifiedModuleBrowserActionResult.userMessage` instead of hand-written per branch copy.

## Contracts

Phase 15 adds or updates contracts for:

- action result destinations and tones
- blocked result wording
- result summaries
- ViewModel action result state
- action execution extraction
- inline result-card wiring
- UI collection of `unifiedBrowserActionResult`

## Next phase hint

Phase 16 should focus on visual polish and density behavior: make the unified cards feel native, tighten empty states, make filters easier to scan, and tune diagnostic density without changing the safe-action execution contract again.

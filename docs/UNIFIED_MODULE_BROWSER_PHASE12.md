# Unified Module Browser · Phase 12 UI Wiring

Phase 12 makes the Phase 10/11 unified browser model visible inside the existing Modules screen without replacing the mature installed-module workflow.

## What changed

- The root Modules tab now collects the unified browser streams from `ModulesViewModel`:
  - `unifiedBrowserControls`
  - `unifiedModules`
  - `filteredUnifiedModules`
- A lightweight unified browser header appears below the provider/device status card.
- The header exposes:
  - view buckets: Installed, Repo, Updates, Scopes, Problems, and GitHub Sources
  - density modes: Comfortable, Compact, and Diagnostic
  - sort menu with all Phase 11 sort modes
  - health filter menu
  - source type chips populated from the current canonical rows
  - provider compatibility chips populated from the current canonical rows
  - active filter summaries and a clear action
- Installed view keeps the existing root-module cards, actions, switch behavior, WebUI/action buttons, snapshots, and update sheets.
- Non-installed unified views render read-only canonical cards that expose source, mode, state, badges, match explanation, aliases, source URL, and scope diagnostics.

## Intentional non-goals

- No full navigation redesign.
- No replacement of the LSPosed tab UI.
- No install/update actions from unified repo/GitHub/rescue rows yet.
- No adaptive list/detail rail yet.

Those belong in later phases after the data-plane and first UI bridge are validated on-device.

## Why this shape

The installed-module screen already owns root manager actions, provider state changes, version locking, snapshots, and WebUI launchers. Replacing it in the same phase as the first unified UI would create a large blast radius. Phase 12 instead uses the unified model as a control and read-only exploration layer while keeping installed modules on their proven cards.

## Contracts added

`UnifiedModuleBrowserUiContractTest` verifies that:

- `ModulesScreen` collects and passes the unified streams.
- `ModulesList` renders the unified header, branches installed vs non-installed views, and wires sort/filter callbacks.
- `UnifiedModuleBrowserPanel` exposes view/density/health/source/provider controls, badge rows, and diagnostic details.

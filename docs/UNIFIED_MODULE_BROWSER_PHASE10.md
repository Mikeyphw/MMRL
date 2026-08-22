# Phase 10 · Unified module browser model

Phase 10 adds a data-only canonical browser model that can represent root modules, repository entries, saved GitHub module sources, LSPosed repository modules, installed LSPosed APK modules, and local/manual sources in one row shape.

## Main entry point

`UnifiedModuleBrowserModel.build(UnifiedModuleInputs)` returns `List<UnifiedModuleItem>`.

The model is intentionally UI-free. Compose screens can consume it later without embedding provider-specific matching rules in cards, chips, or search fields.

## Canonical identity

`UnifiedModuleAliasRegistry` normalizes ids and maps known aliases before rows are merged. It currently covers:

- LSPosed root provider folder variants such as `zygisk_lsposed` and `riru_lsposed`
- Vector provider ids and manager/daemon package names
- ReZygisk naming variants

Every row includes:

- `canonicalId`
- `displayId`
- `aliases`
- `UnifiedModuleMatch`, with reason, confidence, explanation, and matched values

## Source type and mode

Each row has a set of `UnifiedModuleSourceType` values and one merged `UnifiedModuleSourceMode`.

Supported source types:

- `INSTALLED_ROOT`
- `REPOSITORY`
- `GITHUB_SOURCE`
- `LSPOSED_REPOSITORY`
- `LSPOSED_INSTALLED`
- `LOCAL_FILE`

Supported source modes:

- `INSTALLED`
- `REPOSITORY`
- `RELEASE`
- `NIGHTLY`
- `MANUAL`
- `LOCAL`
- `MIXED`
- `UNKNOWN`

Saved GitHub sources are parsed through `GitHubSourceSpec`, so release/nightly mode and artifact strategy carry forward from Phase 9/9B rules.

## Canonical state

`UnifiedModuleState` aggregates install/update state, provider compatibility, LSPosed scope state.

Install states include installed, disabled, removal pending, update pending, update available, ignored, locked, available, problem, and unknown.

Provider compatibility is represented independently as compatible, limited, unavailable, unknown, or not applicable.

## Badges

`UnifiedModuleBadge` exposes badge-ready details without introducing the Phase 11 UI yet. Badge kinds cover:

- provider compatibility
- artifact strategy
- source/mode
- install state
- update
- scope
- problem

Badges also carry severity: info, success, warning, or error. Phase 11 can map these to chips, filters, sort weights, or problem cards.

## Phase 11 preparation

`UnifiedModuleBrowserModel.applyQuery` and `UnifiedModuleBrowserModel.sort` are ready for the upcoming filtering/sorting/search UI.


Sort modes cover installed-first, update-first, problem severity, recently updated, recently installed, most scoped apps, provider compatibility, and name A-Z.

## Current wiring


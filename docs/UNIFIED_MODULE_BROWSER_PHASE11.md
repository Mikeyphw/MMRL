# Unified Module Browser Phase 11

Phase 11 adds the browser control layer on top of the Phase 10 canonical module rows.
It does not replace the installed-module UI yet; it gives the app a stable data contract
for the Phase 12 detail redesign and the later portrait-first shell.

## Added model

`UnifiedModuleBrowserControls.kt` adds:

- `UnifiedModuleView`
  - Installed
  - Repo
  - Updates
  - Scopes
  - Problems
  - GitHub Sources
- `UnifiedModuleDensityMode`
  - Comfortable
  - Compact
  - Diagnostic
- `UnifiedScopeFilter`
  - Any scope
  - Has scope
  - No scope
  - Scope enabled
  - Scope disabled
  - Auto include
- `UnifiedModuleHealthFilter`
  - Any health
  - Healthy
  - Warnings
  - Errors
  - Problems
- `UnifiedModuleBrowserControlsState`
- `UnifiedModuleBrowserStats`
- `UnifiedModuleBrowserControls`

The control state is deliberately free of Compose types. It can be persisted later or
fed by any UI surface without binding the data layer to a specific row layout.

## View buckets

The browser controller maps canonical module rows into the roadmap views:

- Installed includes root modules, installed LSPosed APK modules, and installed saved sources.
- Repo includes normal repository modules and LSPosed repository modules.
- Updates includes update-available, update-pending, locked, and ignored update rows.
- Scopes includes LSPosed installed/repository rows and rows with LSPosed scope state.
- Problems includes warning/error badges or problem install state.
- GitHub Sources includes saved GitHub source rows.

## Filters

The controller can combine all Phase 11 filters:

- install state
- source type
- source mode
- provider compatibility
- scope state
- health severity

Filters are conjunctive. Empty sets mean “do not restrict this dimension”.

## Sorting

Phase 10 already introduced the sort modes. Phase 11 wires them to the browser
controller and defines defaults per view:

- Installed: installed first
- Repo: name A-Z
- Updates: update available first
- Scopes: most scoped apps
- Problems: problem severity
- GitHub Sources: recently updated

## Search

Search supports free text and fielded prefixes:

- `name:` / `title:`
- `id:` / `module:` / `moduleid:`
- `package:` / `pkg:` / `packageid:`
- `alias:` / `aliases:`
- `author:`
- `desc:` / `description:`
- `source:` / `repo:` / `repository:`
- `folder:`
- `scope:`
- `badge:`

Free text continues to use each row's canonical search tokens, including module id,
package id, aliases, author, description, repository/source URL, installed/rescue
folder evidence, and LSPosed scope packages.

## ViewModel wiring

`ModulesViewModel` now exposes:

- `unifiedBrowserControls`
- `filteredUnifiedModules`
- setters for view, density, sort, source types, source modes, provider states, scope filter, and health filter
- shared search text wiring so the existing search bar also feeds the unified browser state

This keeps the current screen usable while making Phase 11 controls available for the
next UI pass.

## Contracts

`UnifiedModuleBrowserControlsTest` covers:

- view bucket membership
- browser statistics
- fielded search behavior
- combined filters
- density-mode contracts
- default sort contracts

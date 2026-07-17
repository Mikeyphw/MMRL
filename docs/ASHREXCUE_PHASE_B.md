# AshReXcue integration phase B

Phase B moves AshReXcue from an isolated companion surface into MMRL's existing navigation and module workflows. It builds on the Phase A singleton manager, lifecycle negotiation, and cached snapshot contract.

## Home protection status

The Home screen now exposes a compact protection card backed by the shared `AshReXcueManager` state. It distinguishes unavailable, update-required, reboot-pending, cached, monitoring, restoration-trial, quarantined, and stable states. The card includes boot-failure progress, quarantine count, protected-module count, cache/read-only state, and the last successful snapshot time.

## Installed modules

MMRL's installed-module list now uses the Phase A snapshot to:

- show protected, trusted, normal, suspect, and quarantined badges;
- filter the normal module list by AshReXcue state;
- change trust state through typed manager operations;
- start a one-module restoration trial for quarantined modules;
- prevent the AshReXcue module itself from being downgraded from protected status.

The embedded AshReXcue module browser is removed from navigation so installed modules have one canonical surface.

## Unified activity

AshReXcue rescue and restoration records are projected into MMRL Activity as read-only entries. They share the normal date grouping, status presentation, details sheet, log copy/share actions, and an AshReXcue filter while remaining owned by the protection module.

## Boot protection settings

AshReXcue settings now live under `Settings > Boot protection`. The screen:

- shows live versus read-only lifecycle state;
- distinguishes active values from values queued for the next boot;
- validates rescue threshold and timeout ranges;
- exposes readiness signals, monitored processes, and missing-process policy;
- supports discarding queued changes;
- restores recommended bundled defaults.

## Module details and theme consolidation

The AshReXcue module details page shows installed and bundled versions, API compatibility, reboot state, and context-aware update/reinstall controls. The embedded AshReXcue UI no longer owns a separate theme selector and inherits MMRL's active Material theme.

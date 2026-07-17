# AshReXcue integration phase C

Phase C retires the remaining embedded companion-style surface and turns the AshReXcue destination into MMRL's native Recovery Center.

## Recovery overview

The overview combines current boot state, failed-boot progress, rescue escalation, the latest incident, quarantine count, and restoration-trial state. It reuses the singleton Phase A snapshot and therefore does not create a second root connection.

## Quarantine and controlled restoration

Quarantine is presented as an owned recovery queue. Users can start a one-module trial, restore half for binary-search recovery, or restore all after an explicit confirmation. Stale quarantine records are visibly separated from actionable disabled modules.

## Rescue sessions

Rescue and restoration events are projected into recovery sessions with status, relative time, details, and active-incident highlighting. The full immutable audit trail remains available in MMRL Activity.

## Restoration trial safety

An active trial exposes explicit complete and rollback actions. Both require confirmation, remain disabled for cached/read-only snapshots, and delegate to the typed AshReXcue manager operations.

## Diagnostics

Recovery Center shows module/API compatibility, snapshot source, last successful refresh, and the typed RootService transport state. Sanitized diagnostic exports preserve their returned archive path in UI state so it can be copied after creation.

## Consolidation

The old AshReXcue bottom-navigation mini-app is no longer used by the destination. Recovery Center inherits MMRL navigation, toolbar, surfaces, snackbar handling, and theme.

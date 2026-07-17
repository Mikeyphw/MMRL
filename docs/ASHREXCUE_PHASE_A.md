# AshReXcue integration Phase A

Phase A replaces the companion screen's repeated privileged reads with a single versioned snapshot contract.

## Typed root API

`ashrexcuectl capabilities` reports API version 2 and supported features.

`ashrexcuectl snapshot [activityLimit]` returns one schema-versioned JSON object containing:

- capabilities
- dashboard
- installed module inventory
- quarantine inventory
- rescue and restoration activity
- active settings
- pending settings

The AIDL root service exposes the same typed operations. It does not expose generic shell execution.

## Module lifecycle

The app independently inspects active and staged root-module directories. It distinguishes:

- missing
- installed
- disabled
- broken control surface
- incompatible API
- outdated bundled version
- staged change requiring reboot
- current
- newer than bundled

Installed and bundled versions are read from their respective `module.prop` files rather than hard-coded in the UI.

## Snapshot cache

The last compatible live snapshot is atomically stored under the app's private files directory. When live root access or the module snapshot fails, the app may display that snapshot with a visible read-only banner. All privileged mutations are rejected unless the current state is live and API-compatible.

## Module installation

Install, update, and reinstall actions are represented explicitly. The bundled ZIP is validated, copied to the app cache, and passed to MMRL's normal module installer through its existing `FileProvider` URI flow.

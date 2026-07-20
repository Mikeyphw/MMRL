# AshReXcue Phase I: Reliability, Performance, and Migration Hardening

Phase I hardens the AshReXcue integration against interrupted writes, stale state, process death, version skew, and repeated background failures. It does not add destructive automation and it does not change the Room database schema.

## State format and migration

The embedded module now publishes snapshot schema 2 and stores a module state-schema marker. Existing installations are migrated under the AshReXcue state lock. Snapshot schema 1 remains readable by the app during the transition.

App-side cached snapshots use a checksummed envelope with a primary and last-known-good copy. A corrupt primary is quarantined and recovered from the backup when possible. Legacy cache envelopes are migrated on read.

## Health assessment and repair

Recovery Center exposes a State health card with the current schema, repair count, summary, and actionable issues. Repair is available only when live root state is writable and the module advertises the `state-repair` capability.

The module repair pass can:

- Re-disable modules from an orphaned restoration trial before clearing the trial.
- Archive restoration state that no longer has a corresponding trial.
- Archive malformed or mismatched quarantine markers.
- Remove stale PID-scoped temporary files.
- Bound rescue, restoration, diagnostics, and corruption-history retention.

All repairs run under the existing state lock and are recorded in the module health report.

## Runtime resilience

Root-service calls reconnect and retry once after a binder failure. Cancellation is never swallowed. Repeated automatic refresh failures are backed off briefly, while manual refresh continues to bypass the backoff.

Automation tokens and receipts are persisted with Android `AtomicFile`, and retained collections are bounded to prevent indefinite growth.

## Warning cleanup

Phase I removes the source and generated-code warnings observed in the Phase H validation log:

- Current Room destructive-migration overloads are used explicitly.
- Kotlin annotation use-site targets are explicit.
- Legacy feed/database compatibility types remain supported without project-wide deprecation noise.
- Clipboard and window-size APIs use their current Compose replacements.
- Userspace-reboot compatibility is isolated behind an API-bounded helper.
- Reflection helpers avoid unchecked `KClass` casts.
- Moshi code generation remains on KSP and is excluded from Hilt's Java annotation-processor path.
- A generated Moshi conversion warning is disabled by its exact Kotlin diagnostic name only.

The experimental AAPT2 override notice is emitted by the Android toolchain used in ARM64 validation and is not an MMRL source warning.

## Version

- AshReXcue module version: `11.5.0`
- Module version code: `250`
- Snapshot schema: `2`

# AshReXcue 11.6.0 Release Notes

AshReXcue 11.6.0 completes the native MMRL integration roadmap.

## Highlights

- Final app-and-module release-readiness gate.
- Live compatibility, state integrity, automation safety, recovery idleness, and performance checks.
- Embedded `ashrexcue.release.v1` self-test.
- Recovery Center release report with explicit passed, warning, and blocker counts.
- Reproducible static, focused Android, and optional full-APK validation lanes.
- Final security invariants and device-matrix checklist.

## Compatibility

- Typed API: 2
- Snapshot schema: 2
- MMRL release protocol: `mmrl.ash.release.v1`
- External automation protocol: `mmrl.ash.external.v1`
- Module automation protocol: `ashrexcue.external.v1`
- Module release protocol: `ashrexcue.release.v1`

The update preserves Phase I durable state, quarantine records, restoration history, automation receipts, and last-known-good snapshot recovery.

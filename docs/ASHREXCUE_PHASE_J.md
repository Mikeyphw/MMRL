# AshReXcue Phase J: Release and Final Parity Gate

Phase J closes the AshReXcue integration roadmap with a reproducible release-readiness contract shared by MMRL and the embedded root module.

## Runtime release gate

MMRL evaluates live root access, module lifecycle parity, typed API compatibility, snapshot schema 2, required capabilities, recovery revision binding, durable state health, restoration idleness, quarantine integrity, queued settings, snapshot freshness, and bounded snapshot size.

The embedded AshReXcue module independently verifies:

- Bundled `jq` availability.
- Executable typed control scripts.
- Module release metadata.
- Writable durable recovery storage.
- State schema and health.
- Absence of an active restoration trial.
- External-control token, receipt, rate-limit, and idempotency storage.
- Readable protection settings.
- The typed mutation surface and `ashrexcue.release.v1` protocol.

MMRL merges both reports into one of three statuses:

- **Ready**: no warnings or blockers.
- **Ready with warnings**: safe to review, but not a clean release candidate.
- **Blocked**: at least one release-critical condition failed.

The report is shown under **Recovery Center → Diagnostics → Release readiness**.

## Protocols and versions

- MMRL release protocol: `mmrl.ash.release.v1`
- Module release protocol: `ashrexcue.release.v1`
- Typed API: `2`
- Snapshot schema: `2`
- AshReXcue module: `11.6.0` (`260`)

## Repository release command

Focused release validation avoids unrelated native assembly:

```bash
scripts/validate-ashrexcue-release.sh --focused
```

Run the static and module self-tests only:

```bash
scripts/validate-ashrexcue-release.sh --static-only
```

On a host with an NDK toolchain matching the host architecture, include full APK assembly:

```bash
scripts/validate-ashrexcue-release.sh --with-apk
```

## Security invariants

- No generic shell or command execution is exposed through AIDL, Tasker, or the embedded control CLI.
- Every privileged Android operation remains a typed AIDL method.
- External mutations remain token-bound, revision-bound, one-shot, idempotent, rate-limited, and auditable.
- Recovery-plan execution revalidates live state before mutation.
- Protected modules cannot enter guarded restoration plans.
- Release-gate checks are read-only.

## Upgrade and rollback expectations

- Phase I state migration remains backward compatible with snapshot schema 1 caches.
- Phase J requires live schema 2 for a clean release result.
- Existing quarantines, restoration history, automation receipts, and last-good snapshots are retained across the module update.
- Downgrading the embedded module may leave MMRL connected but the release gate blocked because `release-gate-v1` is unavailable.

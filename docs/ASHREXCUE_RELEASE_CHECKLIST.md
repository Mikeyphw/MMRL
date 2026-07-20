# AshReXcue Release Candidate Checklist

## Source and contracts

- [ ] `scripts/validate-ashrexcue-release.sh --focused` passes.
- [ ] All Room schemas contain only `formatVersion` and `database` at the top level.
- [ ] AIDL exposes only typed operations.
- [ ] No Android test imports `kotlin.test`.
- [ ] Shell files pass `sh -n` and retain executable permissions.

## Device matrix

- [ ] KernelSU current stable.
- [ ] KernelSU Next current stable.
- [ ] Fresh installation with no AshReXcue state.
- [ ] Upgrade from the Phase I module with existing quarantine and activity history.
- [ ] Reboot after queued settings.
- [ ] Active restoration trial is correctly reported as a blocker.
- [ ] State repair clears only repairable inconsistencies.
- [ ] Root-service process death reconnects and retries once.

## Automation

- [ ] Tasker capability, status, evidence, plan preview, guarded execution, outcome, and refresh actions return protocol `mmrl.ash.external.v1`.
- [ ] A repeated idempotency key returns the original receipt.
- [ ] An expired token is rejected.
- [ ] A changed recovery revision returns `CONFLICT` without restoring modules.
- [ ] High-risk plans require MMRL approval.

## Recovery safety

- [ ] Protected modules cannot be selected.
- [ ] Stale quarantine entries block the whole plan.
- [ ] More than eight modules is rejected atomically.
- [ ] Rollback re-disables every module in the trial.
- [ ] Interrupted trial repair re-disables modules before clearing state.

## UX and accessibility

- [ ] Recovery Center is usable with large font scaling.
- [ ] Release status is conveyed by text, not color alone.
- [ ] Every actionable icon has a content description.
- [ ] Blocker details identify a concrete corrective action.
- [ ] Cached state is clearly marked read-only.

## Release artifacts

- [ ] Focused validation has 0 errors.
- [ ] Full APK assembly is run on a host-compatible NDK toolchain.
- [ ] APK signature and package identity are verified.
- [ ] Embedded module ZIP contains executable scripts and module `11.6.0` (`260`).
- [ ] Diagnostic export contains no secrets or unredacted tokens.
- [ ] Release notes identify protocol and schema versions.

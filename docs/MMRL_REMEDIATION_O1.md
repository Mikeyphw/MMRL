# MMRL remediation O1 — privileged ingress and canonical identity boundary

## Scope

This overlay implements the O1 promise from the BH-01 through BH-64 master remediation pass:

- RC-001 — exported privileged ingress must not accept internal operation semantics;
- RC-002 — module/archive identity used for root/filesystem authority must be canonical and verified;
- RC-003 — module IDs and archive/action paths must be passed to privileged shell commands as arguments, not interpolated shell syntax.

## Delivered invariants

1. `InstallActivity` and `ActionActivity` are internal (`android:exported="false"`).
2. The only exported installation ingress is `ExternalInstallActivity`. It accepts only `ACTION_VIEW` + `content://` + `application/zip`, forwards only read-URI permission, ignores caller operation-control extras, and always enters reviewed install mode.
3. Trusted install/action semantics are no longer serialized directly into Android Intent extras. Internal callers create bounded, process-local opaque launch sessions; the privileged Activities receive only a random session token. Process loss or an unknown/expired token fails closed.
4. External callers cannot select `confirm=false`, rollback mode, operation-history parentage, a trusted expected module identity, or a raw action `ModId`.
5. `ModId` implements the Magisk module ID grammar `^[a-zA-Z][a-zA-Z0-9._-]+$`, rejects alternate root/base directories, and requires a non-empty operational ID before it may become per-module filesystem/root authority. `ModId.EMPTY` remains only a sentinel and cannot resolve per-module authority paths.
6. Installed-module folder identity is authoritative: a folder whose `module.prop` declares another/invalid ID is rejected instead of redirecting subsequent filesystem operations.
7. `ArtifactIdentity` binds an internally selected expected module ID to the archive's parsed `module.prop` ID. The archive ID is reparsed after inspection so the ID and inspection digest describe the same bytes. Known-source install call sites pass their expected IDs; truly external/local arbitrary ZIP selection has no caller-supplied trusted identity.
8. The inspected archive SHA-256 and byte size are captured and reverified immediately before privileged install-command construction; Tasker's reviewed-install execution likewise re-inspects, rechecks its approved SHA-256, and reparses the exact canonical module ID before command construction. Any change blocks installation. O3 still owns operation-scoped immutable staging that closes the remaining pathname TOCTOU window between command construction and backend file open.
9. KernelSU, KernelSU Next, APatch and Magisk install/action/state command construction uses `ShellCommand` single-argument quoting. Legacy and Tasker action execution derive the action path from an operational `ModId`; reboot reasons use the same quoting primitive.
10. Regression coverage was added/updated for canonical module IDs/base directories, the empty sentinel authority guard, shell quoting, artifact-ID mismatch, post-inspection identity changes, archive mutation after review, exact Tasker review identity, narrow external install policy, manifest exposure, and opaque privileged launch extras.

## Deliberate carry-forward

O1 establishes the identity/trust boundary but does not claim the later storage/operation work assigned by the campaign:

- O2: generic privileged filesystem no-follow/canonical containment and Binder/JNI/root-manager correctness.
- O3: operation-scoped immutable staging, complete archive/extraction hardening, authoritative download/bulk transactionality and post-install reconciliation.
- O4: durable operation ownership, cancellation/timeout/idempotency and guarded history state.
- O5: cleanup/migration policy for any legacy invalid module IDs already persisted in Room.

## Campaign validation policy

Per the current remediation-campaign instruction, **intermediate overlays are applied with Devtool `--no-validate`**. O1 therefore does not run its Gradle validation tasks at apply time. Validation is deferred until the final overlay/campaign seal.

The O1-targeted tasks retained for that final validation are:

- `:platform:testDebugUnitTest`
- `:platform:compileDebugKotlin`
- `:app:testOfficialDebugUnitTest`
- `:app:compileOfficialDebugSources`

Artifact-integrity and promise checks performed before packaging are not a substitute for that final Gradle validation. They cover the inventory/source hashes, clean-baseline replay, manifest exposure, opaque-session boundary, canonical identity guards, reviewed-artifact rechecks, and O1 shell-command surfaces.

Suggested intermediate apply command:

```bash
devtool --yes --copy -r ~/Code/MMRL --target mmrl_android apply-overlay \
  --no-validate \
  /path/to/mmrl_o1_privileged_ingress_identity_boundary_overlay_v2.zip
```

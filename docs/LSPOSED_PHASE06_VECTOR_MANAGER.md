# Phase 06 — Vector manager, bounded configuration writes

## Scope of this phase

MMRL now has an explicit Vector module-configuration panel integrated with the LSPosed / Vector discovery screen. It supports:

- Inventory: modules, enabled/disabled **configuration**, installed APK label and detected modern/legacy/unknown format.
- Scoped target inventory per module via authenticated Vector CLI (`scope ls`).
- Explicitly confirmed module enable/disable (`modules enable` / `modules disable`).
- Explicitly confirmed single app scope add/remove for installed, non-system apps, Android user 0 (`scope add` / `scope rm`).
- Fresh pre-write state checks, daemon acknowledgement checks, read-back comparison and visible failure states.

It intentionally does **not** support global scope replacement (`scope set`), system apps, multi-user scope writes, arbitrary CLI commands, database modifications, enabling the Vector root module, or a generic legacy LSPosed write protocol. Modern/legacy APK *detection* is not a guarantee of runtime compatibility. Vector may enforce staticScope exclusions; these errors are surfaced, not bypassed.

## Safety and limitations

The app executes CLI commands through `su -c` from a user-initiated UI operation on an IO dispatcher; the command tokens are strictly validated and allowlisted. Each operation requires an exact confirm dialog, rereads the selected module and scope before writing, checks Vector's success response and performs independent module/scope readback. Unexpected changes demand a refresh. This does **not** provide a transaction across concurrent Vector clients and it does not claim a rollback it cannot prove. No writes occur when the panel mounts. Failure after a write must be manually reviewed in Vector or MMRL before trying again.

Module enabled status means **saved configuration**. A particular target process may still require restart, and true hook execution is not proven without a controlled target-specific test such as the Phase 05 hook lab.

## Hosted CI and device acceptance

A new contract test is added to the existing hosted Devtool GitHub Actions job. The existing same-run, signature-verified debug APK and AndroidTest APK are built as before. The read-only `VectorManagerDeviceTest` instrumentation independently queries the backend inventory and each module's scopes and checks for inventory changes. This is a **read-only** test; it cannot qualify write behavior or active hooks.

After the Phase 06 PR hosted CI passes, use:

```zsh
cd ~/Code/MMRL-lsposed-vector
python3 scripts/mmrl_phase06_device.py RUN_ID localhost:5555
```

The runner writes immutable JSON and an instrumentation log to `~/.local/state/mmrl-phase06/run-RUN_ID-attemptN/SERIAL/`. It never modifies module settings or scopes; only installs/updates the matching MMRL debug and AndroidTest APKs. For **write acceptance**, use the UI on the disposable Phase 05 lab module, verify the original scope/state first, confirm one change, observe Vector CLI readback, then restore manually. The Phase 05 hook lab remains the runtime execution reference.

## Acceptance levels

1. Offline source contract (structural, non-runtime).
2. Hosted full MMRL build + lint baseline + instrumentation APK build (non-device).
3. Physical device read-only manager inventory (`PASS_READ_ONLY_MANAGER_CONFIGURATION`).
4. Manually observed scoped mutation/readback and restoration (not yet automated/qualified).
5. Real hook execution through Phase 05 separate lab (already proven on one device; not proof for all installed modules).

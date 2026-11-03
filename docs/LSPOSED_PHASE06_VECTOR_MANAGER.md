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

The app invokes a narrowly allowlisted libsu `RootService` over Binder IPC from an IO dispatcher; the command tokens are strictly validated and allowlisted. Each operation requires an exact confirm dialog, rereads the selected module and scope before writing, checks Vector's success response and performs independent module/scope readback. Unexpected changes demand a refresh. This does **not** provide a transaction across concurrent Vector clients and it does not claim a rollback it cannot prove. No writes occur when the panel mounts. Failure after a write must be manually reviewed in Vector or MMRL before trying again.

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


## R8 — verified reuse and reversible write acceptance (opt-in)

The existing `mmrl_phase06_device.py` accepts `--reuse-installed` for an
**already installed** exact GitHub APK pair. Both installed APK bytes must match
the pair-receipt hashes after an ADB pull. The runner does not install, replace,
clear or uninstall either package in reuse mode. Each run writes evidence under
`resumes/resume-NNNN` without changing previous receipts.

The separate `VectorManagerLabDeviceTest` is never invoked by ordinary read-only
instrumentation. It requires explicit authorization and a one-time host-issued token placed
in the isolated app **after** the host recovery journal is durably committed.
The ticket is consumed before the first mutation. The test only uses
`VectorManager.change` on `com.mikeyphw.mmrl.hooklab.module` and the single
`com.mikeyphw.mmrl.hooklab.target/0` scope. Initial module state must be
disabled and scope must be empty or exactly this one target. It exercises
add/remove scope, enable/disable, verifies each read-back, and restores the
exact lab baseline. It does not claim proof of actual injection.

After a successful new R8 GitHub Actions run, perform the normal read-only
acceptance on the new run's isolated app pair first. Then, and only after
reviewing the lab changes, run:

```zsh
python3 scripts/mmrl_phase06_lab_write.py RUN_ID localhost:5555 --authorize-lab-changes
```

The host validates both same-run GitHub APK pairs, checks exact hashes of each
already-installed MMRL APK, and installs **only missing** disposable lab APKs
without overwriting any existing APK. If an installed lab APK differs, the test
refuses. A fsynced host journal is committed before instrumentation, and host
recovery independently checks/restores the disposable module state and scope.
If the process is interrupted, use the printed journal location:

```zsh
python3 scripts/mmrl_phase06_lab_write.py RUN_ID localhost:5555 \
  --authorize-lab-changes --recover PATH_TO_JOURNAL_JSON
```

Do not rerun acceptance while a journal is in `RECOVERY_REQUIRED` or an
unknown partial state. No other installed Xposed modules are write targets.
A successful build or read-only test is not write acceptance.

## R8.4 — Vector v2.2 implicit enable safety

Vector v2.2's `scope add` calls `setModuleScope()`, which also calls
`enableModule()`. Consequently, **adding a scope to a disabled module can
silently enable it**. Phase 06 now refuses `ADD_SCOPE` on disabled modules:
users must explicitly enable first through the normal confirmation dialog.
The panel disables its Add control and explains this requirement.

The disposable lab now performs explicit enable, scope flip/undo, explicit
disable, all with read-back and journaling. On-device and host restoration
restore scopes before the final disable, because a scope addition can enable
a module. This does not change the protected set of allowed write targets,
one-time authorization, source/receipt verification, or original-state
restoration requirements.

Source-only patch: device APKs must be rebuilt by GitHub Actions for R8.4.
The prior `41551130290` APKs must NOT be used for R8.4 write qualification.

# Phase 05 — Real hook execution on a disposable target

## Scope

This is a functional Xposed injection test, separate from Phase 04's read-only
MMRL screen/runtime device verification. It adds two tiny *test-only* Android
applications built by GitHub Actions (not within MMRL itself):

- `com.mikeyphw.mmrl.hooklab.target`: one exported, read-only ContentProvider at
  `content://com.mikeyphw.mmrl.hooklab.target/probe`. The reply invokes
  `ProbeValue.readValue()` and is normally `ORIGINAL_PHASE05`.
- `com.mikeyphw.mmrl.hooklab.module`: one modern libxposed API 102 hook of that
  one target method, returning `HOOKED_PHASE05`. Its signed APK has
  `META-INF/xposed/java_init.list`, `module.prop` with `staticScope=true`,
  and `scope.list` containing **only** the disposable target app.

## Hosted CI

`hook-lab` is a separate GitHub Actions job that depends on the existing
MMRL Devtool APK, Lint baseline, and AndroidTest acceptance *build* gates. It
builds both APKs on the same GitHub host and verifies package identifiers,
signer digests, exact modern Xposed entry and fixed scope, then publishes a
source-bound receipt. Neither the CI job nor a successful APK build proves
runtime hooks or qualifies an actual device.

## Device prerequisites

- Physical Android device with root and Vector installed/working (Phase 04
  already tested daemon communication, but API 102 is required here).
- Root consent via `su`; primary Android user (0); `adb`, `gh`, `python3` in
  Termux; internet access to GitHub Actions artifacts.
- Test module must initially be disabled. If previously enabled or scoped to
  another app, the runner refuses rather than overriding existing settings.
- The runner's explicit `--authorize-lab-changes` flag is required. It installs
  or updates **only the two uniquely named lab APKs**, may add the lab target to
  the lab module's scope, enables that lab module temporarily, force-stops
  **only the lab target**, and restores the original module state and scope.

## Execute (after hosted CI succeeds)

```zsh
cd ~/Code/MMRL-lsposed-vector
python3 scripts/mmrl_phase05_device.py RUN_ID localhost:5555 --authorize-lab-changes
```

The runner validates the run branch and exact source SHA/hash receipt before
installing. It requires Vector API >=102, physical hardware properties and
Android user 0. It journals the *original* module state and scope before any
Vector configuration change; the JSON results never mark an unobserved hook
as verified. Expected read values: `ORIGINAL_PHASE05` → `HOOKED_PHASE05` →
`ORIGINAL_PHASE05` after disabling the lab module and restarting only the target.

Outputs are under `~/.local/state/mmrl-phase05/run-RUN_ID/SERIAL/`:
`pair/`, `journal.json`, and `hook-execution.json`.

## Interrupted run / recovery

If the run stops after `INTEND_SCOPE_ADD` or `INTEND_ENABLE` or reports
`RECOVERY_REQUIRED`, **do not assume restoration happened**. Use the saved
journal to restore only the exact test module/scope:

```zsh
python3 scripts/mmrl_phase05_device.py RUN_ID localhost:5555 \
  --authorize-lab-changes \
  --recover "$HOME/.local/state/mmrl-phase05/run-RUN_ID/localhost:5555/journal.json"
```

Do not delete the journal or repeat normal runs until restore reports
`RESTORED`. Scope or root-provider surprises cause a safe refusal, not a broad
reset. No uninstall, device reboot, root-module changes or system app scope
changes are part of this phase.

## Interpretation

`PASS_HOOK_AND_RESTORATION` requires **three independently queried values** on
real Android: baseline, hooked, restored. Any missing / unexpected value,
module mismatch or incomplete restoration is a failure/inconclusive outcome.
The test proves injected hook execution for the disposable target only, not
system_server injection, all app compatibility, or production readiness.

References:
- https://github.com/JingMatrix/Vector
- https://github.com/JingMatrix/libxposed-api
- https://libxposed.github.io/api/io/github/libxposed/api/package-summary.html

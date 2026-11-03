# Phase 07 R1 — Vector manager diagnostics and navigation

## Source authority

Based on the merged `lsposed-vector` tree after PR #6 (merge `b8410ce024b921d96c384e7b45d26929625418ab`).
Apply to an updated Termux checkout; the patch checks exact Git blob IDs before writing.
It does not replace or incorporate Phase 06 overlay ZIPs.

## Changes

- The existing libsu Binder connection remains on the Android main looper and all
  CLI operations remain off-main via the same fixed AIDL interface. Differentiate:
  `BIND_DISPATCH_FAILED`, `BIND_INTERRUPTED`, `BIND_TIMEOUT`, and `BIND_FAILED`.
  Exception reports contain a bounded reason or exception *type*, never raw root logs.
- Show a root authorization / refresh hint only for binding errors. Crucially,
  do **not** claim rollback or guaranteed no writes after an unknown failure.
- Show enabled/total module count and local, case-insensitive label/package search.
  Filtering never triggers Vector IO and does not mutate module configuration.
- Clear a pending target string on refresh, module switch and scope collapse.

## Preserved boundaries

- No raw shell, no additional privileged commands, no scope set or broad write.
- No implicit enable of a disabled module while adding scope.
- No automatic writes; configuration requires confirmation and read-back.
- No claim that hook execution or injection is verified by this phase.

## Validation

Run all Phase 06 source regression tests and `scripts/test_mmrl_phase07_r1_contract.py`.
Android/Gradle compilation must run through GitHub Actions, never Termux.
On-device acceptance: new run-specific isolated APK pair, read-only inventory,
then only if needed targeted disposable-lab validation with preserved journals.

The R1 patch has source-contract validation but is NOT Android-runtime-qualified
until the hosted workflow succeeds and new read-only device acceptance passes.

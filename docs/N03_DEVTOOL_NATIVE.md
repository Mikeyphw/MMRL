# N03 — MMRL `next` native Devtool integration

## Scope and source

- Base: `Mikeyphw/MMRL:next` at `ed58f97b37e0f06acbca085c3de0e2f8abc1ef30` (N02 passing GitHub Actions build).
- Devtool implementation pin: `Mikeyphw/devtool` at `bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c`.
- No relationship to the old fork `master` or the LSPosed-Vector feature branch. `next` retains its upstream `MMRLApp/MMRL:master` ancestry.
- No LSPosed, Vector, hook, scope, or root-mutation code in this integration.

## Architecture

`[targets.mmrl_next]` owns three independent native `android-gradle` jobs: `:app:assembleOfficialDebug`, `:app:lintOfficialDebug` and `:app:testOfficialDebugUnitTest`. The separate `next-devtool-native.yml` workflow uses a precisely pinned Devtool checkout, native `compile_graph` / `run_node` and a verified same-run debug APK receipt.

Only a GitHub-hosted Linux Actions runner executes Android/Gradle tasks. `scripts/next-devtool.zsh` is read-only locally; `MMRL_NEXT_DEVTOOL=1` provides a stable output filename only on that hosted CI path, preserving normal MMRL APK names outside Devtool CI.

No production keystore, debug-signing identity management, APK installation, emulator or privileged device access is introduced. A receipt proves only same-run source/output consistency and debug APK signature; it does not certify a release.

## Acceptance sequence

1. Apply N03 to the existing MMRL checkout, branch from source-locked `next`, auto-commit/push and open a non-draft PR targeting `next`.
2. The legacy `next-android-ci.yml` must still pass; the new `next-devtool-native.yml` must compile the graph, build, verify, lint and run JVM unit tests.
3. Inspect the uploaded `mmrl-next-n03-native-...` artifact and its SHA-256/commit/graph identity.
4. Merge the PR only after both workflows pass. Retire the legacy standalone CI in a later PR to avoid duplicative builds.
5. Continue N04 by explicitly enrolling `MMRL:next` and `MMRL-LSPosed:main` in Devtool federation and adding a target-owned cross-repository validation DAG.

## Local read-only operations

```zsh
zsh scripts/next-devtool.zsh state --json
zsh scripts/next-devtool.zsh config --json
zsh scripts/next-devtool.zsh doctor --json
```

The wrapper intentionally does not provide a local Android `run`, `build`, `test`, `install`, signing, or release operation.

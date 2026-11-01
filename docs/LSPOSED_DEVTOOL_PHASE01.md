# MMRL LSPosed/Vector upstream integration — Phase 01

This branch begins from the original `MMRLApp/MMRL` `master` commit
`6c2b778867f7f66ee2d208ee82887aa2cf9bb0bb`. The `upstream-base` mirror is
not modified by this change. Original repo workflows remain checked in for
tracking purposes but are disabled on this fork via a repository identity
guard in `release-ci.yml`, to keep Android/Gradle execution under Devtool.

## CI contracts

- Devtool provider SHA: `246f168ebdef6d9b25c194268c810c877d099fa3`.
- Devtool target: `mmrl_lsposed_ci`, workflow `ci`, one native job.
- Reviewed tasks: `:app:lintOfficialDebug`, `:app:assembleOfficialDebug`.
- Original upstream Gradle wrapper: 9.1.0, SHA-256 pin unchanged.
- Reviewed wrapper JAR SHA-256: `d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd`.
- Java 21, Android API 36, Build Tools 36.0.0, Ubuntu 24.04.
- Hosted GitHub Actions dependencies are immutable-SHA pinned to Node 24 runtime revisions, avoiding Node 20 deprecation warnings.
- CI opt-in Gradle property `mmrlLsposedHosted=true` only changes the debug APK filename.
- Exact debug APK output: `app/build/outputs/apk/official/debug/MMRL-LSPosed-CI-OfficialDebug.apk`.
- Package `com.dergoogler.mmrl.debug` is **temporary Phase 01 upstream identity**.
  A separate `com.mikeyphw.mmrl.lsposed` identity is a future guarded phase,
  and must precede co-installation or device acceptance.
- CI verifies APK package, debug signing certificate, bytes, and Devtool node
  handoff receipts. Success is debug-only: no device execution or release qualification.
- `GH_TOKEN` comes from the GitHub Actions token for upstream package retrieval.
  GitHub Packages in another repository might still require a separate package
  grant. Do not silently copy private dependency binaries or rewrite Gradle
  repositories to conceal missing permission.

## Trigger policy

The build runs for PRs into `lsposed-vector`, and pushes to `lsposed-vector` and
`feature/lsposed-ci-baseline`. Normal `workflow_dispatch` is not included because
GitHub only registers manually dispatched workflows from the repository default
branch (`master`), which is intentionally unchanged.

## Scoped changes

1. `.devtool.toml` adds only the hosted CI target and one DAG node.
2. `scripts/mmrl_lsposed_hosted.py` checks graph invariants and executes native
   `compile_graph` / `run_node` with immutable GitHub run context.
3. `.github/workflows/mmrl-lsposed-devtool.yml` provisions host dependencies;
   it does not call Gradle directly.
4. `app/build.gradle.kts` gets one property-gated debug output mapping.
5. `.github/workflows/release-ci.yml` disables the inherited non-Devtool
   release job matrix on this fork only.

No LSPosed production code, theme engine, Tasker, custom dependencies, or
legacy fork overlay files are added in Phase 01.

## Acceptance

A green GitHub workflow must provide `mmrl-lsposed-officialdebug-*` containing
`node-01/payload/` and `receipt.json`, plus `mmrl-lsposed-ci-evidence-*`
containing `plan.json` and `verification.json`. Check that
`device_qualified=false`, `release_qualified=false`, and the APK SHA-256
matches the receipt.

**Do not mark Phase 01 complete until the real GitHub-hosted run passes.**
A GitHub package-access failure is an environment/dependency blocker and
should be fixed without introducing unrelated fork infrastructure.

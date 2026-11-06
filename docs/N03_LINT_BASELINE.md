# N03 HF02 — source-bound Android Lint baseline

## Why this exists

The N03 `MMRL:next` Devtool CI was blocked by `:platform:lintAnalyzeDebug` crashing in `SuFileReadWrite.kt`. HF01 fixed that single lint-engine bug by disabling only `UElementAsPsi` inside the hosted Devtool environment. The subsequent N03 CI run passed app compilation and the lint analyzer, then stopped because existing MMRL lint findings were treated as new build-breaking errors.

- GitHub run: https://github.com/Mikeyphw/MMRL/actions/runs/42159873601
- Source PR branch head: `e53a8f1a088f7a264f58b7424c731890c993eec1`
- Lint report from the run's `mmrl-next-n03-lint-42159873601-attempt1` artifact.
- Original report SHA-256: `e56736383d1902e0fb6e62913e08fd31c6495b9f94ecc8c289f8b4f1aef30958`.
- Baseline: **536 known findings** (302 errors, 232 warnings, 2 hints).
- Root cause of first reported issue: `android:windowLightNavigationBar` in `app/src/main/res/values/themes.xml` requires API 27, but MMRL supports API 26.
- The baseline is exactly the report's issue set with only GitHub runner absolute file paths changed into paths relative to `app/`.

## Behavior

The `app/lint-baseline.xml` file is enabled **only** when all three environment variables match the native hosted N03 workflow (`MMRL_NEXT_DEVTOOL=1`, `GITHUB_ACTIONS=true`, `GITHUB_REPOSITORY=Mikeyphw/MMRL`). All normal MMRL developer builds retain their ordinary lint rules. The Devtool native APK, lint, and JVM tasks remain independent and source pinned.

This uses Android's official lint baseline mechanism. Existing findings in the baseline are ignored for CI failure; **new errors still fail the build**. No `abortOnError=false`, no broad `disable`, and no reduced lint check set is added here. The platform-only `UElementAsPsi` crash workaround from HF01 remains unchanged.

### Remediation

Treat this as tracked technical debt, not a clean lint bill of health. Fix existing issues in focused PRs (start with `NewApi` / API 26 correctness), then remove those exact baseline entries to avoid hiding their reintroduction. When updating upstream source, refresh the source-locked issue inventory under review; do **not** auto-regenerate or overwrite the baseline in ordinary CI.

## Validation limits

XML integrity, 536 issue records, path portability, source guards and static regression checks are locally testable without Gradle. Whether AGP 9.0 matches all baseline findings is **not proven** until the actual GitHub Actions N03 run completes. Android JVM tests likewise remain pending.

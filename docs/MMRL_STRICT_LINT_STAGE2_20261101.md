# MMRL strict Lint Stage 2 — 20-finding remediation

Source authority: `Mikeyphw/MMRL` master at `d3f3bb77a87c4f4b5655a0671cedc5dc0b011681`.
Evidence: GitHub [run 41267589980](https://github.com/Mikeyphw/MMRL/actions/runs/41267589980), strict `officialDebug` lint text report uploaded as artifact 13067369182.

## Changes by issue count

| Finding | Count | Implementation |
|---|---:|---|
| `AndroidGradlePluginVersion` | 4 | Add this *one* advisory-version issue to existing explicit ignore list, while preserving pinned Gradle 9.7.1/AGP 9.3.2 and independent stable toolchain/hygiene checks |
| `ComposableNaming` | 3 | Scope `@Suppress` to three intentionally lowercase DSL functions; preserve public APIs |
| `ConfigurationScreenWidthHeight` | 3 | Read live window container pixels via `LocalWindowInfo` and `LocalDensity` instead of `LocalConfiguration` |
| `ModifierParameter` | 2 | Move BBCodeText modifier to first optional parameter and rename private ExpandedMainLayout modifier |
| `StringFormatCount` | 3 | Restore `%1$s` in Tamil, Arabic and Hindi; keep locale strings and base contract |
| `ObsoleteSdkInt` | 2 | Remove redundant guards in minimum-supported API paths |
| `UsableSpace` | 2 | Narrowly annotate the immediately-free-bytes helper for immutable staged/rollback artifacts to retain fail-closed storage behavior |
| `UseKtx` | 1 | Use AndroidX core `SQLiteDatabase.transaction` (exclusive) for the LSPosed copy edit; keep WAL checkpoint after transaction |

No `abortOnError`, `warningsAsErrors`, `checkDependencies`, signatures, device gates, or production release boundaries are relaxed. `SdCardPath` deliberate policy remains intact from Stage 1. GitHub-only Gradle acceptance is still required: a source patch is not proof of a clean lint run.

## External acceptance

Push to master after review, let `.github/workflows/mmrl-release-seal.yml` run on GitHub and require **four successful jobs** (static, debug, release, Instrumentation contracts) and no strict-Lint errors. Reports remain attached on failure. The successful R36 hosted-debug consumer run remains separately qualified as non-release.

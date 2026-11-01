# Phase 01 Android Lint baseline review — pre-existing upstream findings

**Source:** `Mikeyphw/MMRL` PR #1, branch `feature/lsposed-ci-baseline`, head `6edd909b635499c8791479a9520748e988619617`; GitHub Actions run `41359564433` (the PR build checked out its merge SHA `6e3f062be8fdb2eca00ef9feb44b3aa88fe27b7b`).

**Captured evidence:** GitHub Actions artifacts `13104838114` (candidate and verification receipt) and `13104951104` (original Lint XML/HTML/text). The actual Lint XML was independently parsed and its normalized fingerprint compared with the candidate. Digest: `d67d2e0b04142ae295bb9462b85e5128ad186e87ea5ebb1c3fd95bb208baf7e7`.

## Finding inventory (536 total)

| Severity | Occurrences |
|---|---:|
| Error | 302 |
| Warning | 232 |
| Hint | 2 |

| Issue ID | Occurrences | Triage |
|---|---:|---|
| `MissingTranslation` | 300 | Existing incomplete Italian translations, significant localization debt |
| `UnusedResources` | 125 | Cleanup candidate; verify resources accessed dynamically before deleting |
| `GradleDependency` | 40 | Dependency notices; upgrade independently with CI and compatibility verification |
| `NewerVersionAvailable` | 36 | Dependency update suggestions; verify latest compatibility first |
| `Typos` | 6 | Polish translation texts |
| `UnusedTranslation` | 6 | Locale configuration and translation coverage mismatch |
| `TypographyEllipsis` | 4 | Typography cleanup |
| `AndroidGradlePluginVersion` | 3 | Review toolchain pins without blindly upgrading |
| `PluralsCandidate` | 3 | Localization correctness |
| `NewApi` | 2 | **Priority: API 27 navigation bar attribute in app theme, minSdk 26**; move to version-qualified resource or fix semantics |
| `StringFormatCount` | 2 | **Priority: Arabic and Hindi translated format argument count mismatch**; can affect runtime formatting |
| `SimilarGradleDependency` | 2 | Dependency version duplication hints |
| `UseTomlInstead` | 2 | Move dependency coordinates to version catalog |
| `AppBundleLocaleChanges` | 1 | **Priority: dynamic locale changes without verified language-split handling** |
| `DefaultUncaughtExceptionDelegation` | 1 | **Priority: exception handler does not delegate to previous default handler** |
| `ModifierParameter` | 1 | Compose API style |
| `ObsoleteSdkInt` | 1 | Resource qualifier cleanup |
| `UseOfNonLambdaOffsetOverload` | 1 | Compose performance warning |

## Scope and acceptance policy

This baseline acknowledges the existing upstream warnings/errors **as technical debt only**; it is not proof of correctness, safety, release qualification, or device acceptance. The highest-risk items above should be fixed in focused subsequent PRs. The source-controlled gate preserves the exact issue ID, severity, path, normalized message, and occurrence count; **new findings and increases in existing counts still fail CI**, and resolved findings may drop below baseline. Internal Lint crashes and missing reports remain fatal.

The reviewed baseline is not a license to suppress new faults, skip future analysis, or ignore issues when upgrading upstream. Approval is deliberately separate from the APK assembly receipt and does not alter APK signatures or application code.

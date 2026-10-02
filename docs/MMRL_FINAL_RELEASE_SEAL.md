# MMRL final release seal

This document is the authoritative release contract for the personal-use MMRL branch. MMRL is one Android application plus its internal library modules; there is no Play Store distribution lane.

## Current toolchain

- Gradle 9.7.1
- Android Gradle Plugin 9.3.2
- Kotlin 2.4.10 / Kotlin reflect 2.4.10
- KSP 2.3.11
- Hilt 2.60.1
- Java 21
- compileSdk / targetSdk 36
- Build Tools 36.0.0
- NDK 29.0.14206865
- CMake 3.22.1

## Authoritative release

The release authority is Devtool AOPS04:

```sh
scripts/run-mmrl-release-seal.sh
# -> devtool --repo . --target mmrl_android release
```

The `officialRelease` lane is explicitly release-qualified. Devtool runs repository/toolchain/product hygiene, host native contracts, `:app:testOfficialReleaseUnitTest`, strict `:app:fullLintOfficialRelease`, `:app:assembleOfficialRelease`, signature verification, signer-identity verification, configured device instrumentation, install smoke, optional upgrade smoke, and immutable release-evidence sealing.

The root Gradle `mmrlReleaseSeal` task remains a project-domain aggregate for compatibility and manual Gradle inspection; it is not the execution authority.

Release signing remains project-owned through `signing.properties`. Devtool declares `mode = "project"` with `policy = "release-required"`, so credentials stay out of Devtool config while produced artifacts are still verified with the canonical signing service. G2 prefers the developer's real ignored signer when present; if none is configured, the gate creates a disposable validation signer (matching the CI model) so qualification does not require production secrets on-device.

## Persistent validation

MMRL's configured validation phases are durable Devtool checkpoints. Use `./devtoolw validate-session`, `./devtoolw validate-session --complete`, or `./devtoolw validate-session --retry-failed --complete`. Successful unchanged phases are reused; no `clean` is inserted between phases. Less-common validation controls can be passed after `--` or invoked through Devtool's native `validate` control surface.

## Connected-device validation

The canonical instrumentation lane is `officialDebug`. ADB provider/device selection comes from `[targets.mmrl_android.android.adb]`; instrumentation build/install/run/cleanup and logcat/screenshot failure evidence come from Devtool Android Operations. `./devtoolw device` runs the EXO `device-native` workflow.

Release instrumentation is `required`, matching the required install-smoke boundary: a full release must admit exactly one device and qualify instrumentation before shipping. Direct `./devtoolw device` instrumentation uses the same required policy. `MMRL_ADB_SERIAL` may be supplied by the thin frontend and is projected to `ANDROID_SERIAL`; the repository no longer parses `adb devices` or invokes raw Gradle itself. The explicit package-only release mode disables device gates.

## Evidence and hygiene

A release session records Git/Devtool identity, Android providers, selected variant/device, signing policy and certificate identity, gate results, artifact SHA-256 values, and the final verdict. Evidence is immutable and registered through Devtool's run/artifact machinery.

The active source tree must not retain backup/scratch files or generated APK/AAB/AAR artifacts. Runtime Devtool state is local execution metadata, not source.

## Termux resource policy

Validation remains Android-safe and constrained: one worker, CPU limit 1, no Gradle parallel execution, wrapper Gradle, Configuration Cache off, memory guard disabled, and bounded phase heaps. This resource policy does not remove any source/test/lint/release gate.

## Durable validation before release

For long Termux qualification runs, use:

```bash
./devtoolw validate-session --complete
```

Devtool checkpoints each Android validation phase under `.devtool/state/android-validation/`, preserves Gradle/build caches between phases, and can resume/retry failed phases without replaying already-qualified predecessors whose inputs remain valid. This validation session complements AOPS04 release evidence; it does not replace the canonical `devtool release` run.

## Validation action ownership

The canonical `validate` action is Devtool's native Android resumable pipeline. The repository-specific EXO DAG is separately named `repository-check`; this avoids action shadowing while retaining explicit EXO/TLJ coverage.


# MMRL final release seal

This document is the authoritative release contract for the feature-rich personal-use MMRL branch after consolidated overlays OV01 through OV05.

MMRL is one Android application plus its internal library modules. The standalone recovery application is not part of this repository's product graph. There is no Play Store distribution lane.

## Current toolchain

- Gradle 9.7.1
- Android Gradle Plugin 9.3.2
- Kotlin 2.4.10
- Kotlin reflect 2.4.10
- KSP 2.3.11
- Hilt 2.60.1
- Java 21
- compileSdk / targetSdk 36
- Build Tools 36.0.0
- NDK 29.0.14206865
- CMake 3.22.1

## Authoritative host seal

The root Gradle task `mmrlReleaseSeal` is the aggregate host-side contract. On Termux, `scripts/run-mmrl-release-seal.sh` executes the same contract in memory-bounded phases so Android LMKD/daemon pressure does not turn one very large Gradle invocation into a false source failure.

The required host gates are:

- `python3 scripts/validate-mmrl-source-hygiene.py`
- `verifyStableToolchainBaseline`
- `verifyMmrlProductBoundary`
- `verifyRepositoryHygiene`
- `:platform:testDebugUnitTest`
- `:platform:testNativeContracts`
- `:app:testOfficialDebugUnitTest`
- `:app:compileOfficialDebugSources`
- `:app:processOfficialDebugResources`
- `:app:compileOfficialDebugAndroidTestKotlin`
- `:app:fullLintOfficialDebug` with `mmrl.fullLint=true`
- `:app:assembleOfficialDebug`
- `:app:assembleOfficialRelease`
- `:app:verifyReleaseArtifacts`

A release build requires a complete `signing.properties`; unsigned or debug-signed release artifacts are rejected.

## Connected-device validation

`scripts/run-mmrl-device-validation.sh` implements the optional device gate. `MMRL_RUN_CONNECTED_TESTS=auto` runs instrumentation when exactly one authorized adb device is available and otherwise records an explicit skip. Set `MMRL_RUN_CONNECTED_TESTS=1` to make device availability mandatory and `MMRL_ADB_SERIAL` when more than one device is connected.

The connected gate runs `:app:connectedOfficialDebugAndroidTest`, covering installed-manifest behavior, FileProvider/content URIs, Room migration contracts, and WorkManager/lifecycle contracts.

After connected tests, an optional non-mutating root smoke is executed only when `su -c id` actually reports `uid=0`. Lack of root is a skip, not a failure.

## Repository hygiene

The active source tree must not retain `*.bak`, `*.orig`, `*.rej`, `*.before-*`, editor backups, or generated APK/AAB/AAR artifacts. Runtime Devtool state is local metadata and is not treated as source.

Historical subsystem design documents may remain for feature provenance, but they are not alternative product release seals. This file, the root `mmrlReleaseSeal` task, the release runner, and the CI workflow define the current ship gate.

## Termux resource policy

Final validation uses one worker, CPU limit 1, no Gradle parallel execution, the project wrapper, memory guard disabled, and bounded heaps (768 MiB for build/package and 640 MiB for unit/lint where configured). This is deliberate for Android/Termux stability and does not weaken any source/test/lint gate.

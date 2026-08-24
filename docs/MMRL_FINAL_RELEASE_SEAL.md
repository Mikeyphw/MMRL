# MMRL release seal

MMRL is a personal-use Android module manager with one application module and a
fixed set of internal library modules. The release seal validates the current
product directly rather than replaying historical overlay-specific gates.

## Required gates

Run from a clean checkout:

- `python3 scripts/validate-mmrl-source-hygiene.py`
- `./gradlew verifyStableToolchainBaseline verifyMmrlProductBoundary`
- `./gradlew :platform:testDebugUnitTest`
- `./gradlew :platform:testNativeContracts`
- `./gradlew :app:testOfficialDebugUnitTest`
- `./gradlew :app:lintOfficialDebug -Pmmrl.fullLint=true`
- `./gradlew :app:assembleOfficialDebug`
- `./gradlew :app:assembleOfficialRelease`
- `MMRL_RUN_CONNECTED_TESTS=1 scripts/run-mmrl-release-seal.sh` when an Android device or emulator is available.

## Current baseline

- Gradle 9.7.1
- Android Gradle Plugin 9.3.2
- Kotlin 2.4.10
- KSP 2.3.11
- Hilt 2.60.1
- Java 21
- compileSdk / targetSdk 36
- Build Tools 36.0.0
- NDK 29.0.14206865
- CMake 3.22.1

The personal-use branch has no store-distribution build lane. Connected
instrumentation remains optional unless explicitly required for a release run.

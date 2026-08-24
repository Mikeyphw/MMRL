# MMRL toolchain and product boundary

MMRL is built as one personal-use Android application plus its internal library
modules. The repository boundary is defined by an allowlist rather than by
special-casing any external application which may have shared this source tree
historically.

## Gradle projects

- `:app`
- `:hidden-api`
- `:platform`
- `:ui`
- `:ext`
- `:datastore`
- `:terminal-compat`
- `:webui-core-compat`
- `:compat`
- `build-logic` as the local convention-plugin included build

Only `:app` applies the Android application plugin.

## Stable toolchain

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

## Termux / Devtool execution

The project wrapper is authoritative. Devtool uses one worker for heavy Android
phases, disables its memory guard for this target, uses phase-specific bounded
heaps, and does not run Gradle in parallel. The active SDK/NDK/QEMU trees and
Gradle dependency caches are preserved during low-storage cleanup.

Run the lightweight structural gates with:

```bash
./gradlew verifyStableToolchainBaseline verifyMmrlProductBoundary
python3 scripts/validate-mmrl-source-hygiene.py
```

Full build, unit, lint, release, and optional instrumentation gates are handled
by the release-seal workflow.

# MMRL R36 — named OfficialDebug native hosted DAG consumer

Source authority: `Mikeyphw/MMRL` default `master`, commit
`44300fa394c144ea86e35141725c49ce02794cef` at time of adoption.
Devtool runtime: `246f168ebdef6d9b25c194268c810c877d099fa3`.

The existing `mmrl_android` Termux, device, root, release signing, and
release-seal workflows remain untouched. New target `mmrl_hosted_r36`
only admits `:app:lintOfficialDebug` and `:app:assembleOfficialDebug` as a
single exact R36 semantic Gradle node on Ubuntu hosted GitHub Actions.

MMRL normally produces a version-dependent APK filename, so the new debug
source sets `MMRL-R36-OfficialDebug.apk` **only** when the trusted hosted CI
environment passes `ORG_GRADLE_PROJECT_mmrlR36HostedDebug=true`. Ordinary
OfficialDebug builds and all release builds retain existing versioned names.

The workflow provisions JDK 21, pinned Gradle wrapper 9.7.1,
Android platform/build tools 36, NDK 29.0.14206865 and CMake 3.22.1.
It fetches pinned Devtool at the exact accepted R36-R1 commit and invokes
its native RM-06 `run_node` through `scripts/mmrl_r36_hosted.py`; it never
invokes Gradle directly from workflow code. Devtool remains the execution
and graph/receipt authority.

The hosted verification stage checks the native handoff receipt, SHA-256,
actual APK package `com.mikeyphw.mmrl.debug` and debug APK signature using
Android SDK tools. Uploaded artifacts are debug-only; native receipt's
`authenticated=false` remains truthful because GitHub artifact transport
itself is not independently authenticated through R35 importer.

## Real hosted consumer acceptance — 2026-11-01 UTC

MMRL hosted acceptance passed on source SHA
`66ef3d94f2cbddb5c54de6f174d0676f8b5169db`:

- [MMRL OfficialDebug GitHub Actions run 41262381830](https://github.com/Mikeyphw/MMRL/actions/runs/41262381830): **success**. Devtool R36 semantic native DAG ran `:app:lintOfficialDebug` and `:app:assembleOfficialDebug`, Gradle reported `BUILD SUCCESSFUL`, and the native receipt says `LOCAL_JOB_SUCCEEDED`.
- The MMRL verification recorded `VERIFIED_DEBUG_NON_RELEASE`, package `com.mikeyphw.mmrl.debug`, `package_independently_verified=true`, and `signature_independently_verified=true`. APK SHA-256: `9ba028c77eb53889560b38a13c145278630693306aa25747eb54407608e760c8`. [APK plus native DAG receipt](https://github.com/Mikeyphw/MMRL/actions/runs/41262381830/artifacts/13065129727) and [verification evidence](https://github.com/Mikeyphw/MMRL/actions/runs/41262381830/artifacts/13065144673) were uploaded.
- [DustUI GitHub Actions run 41258640269](https://github.com/Mikeyphw/dustui/actions/runs/41258640269): **success**. The nested `android/` project exercised the R36 semantic DAG with `:app:lintDebug` and `:app:assembleDebug`, Gradle reported `BUILD SUCCESSFUL`, and the native receipt says `LOCAL_JOB_SUCCEEDED`. [Debug APK and DAG artifact](https://github.com/Mikeyphw/dustui/actions/runs/41258640269/artifacts/13064262860) was uploaded.

Together these consumer runs establish R36 **nested project-root, semantic hosted execution, grouped reviewed debug tasks, and named debug variant** acceptance evidence.

**Explicit exclusions:** neither workflow establishes independently authenticated downloaded artifact provenance, production release signing, connected-device acceptance, or release qualification. These remain separately gated. The R35 exact-run authenticated importer has its own evidence and must not be inferred from these uploads.

## Acceptance

Source admission and offline tests are not hosted execution evidence.
After applying the overlay, push `master` and dispatch
`devtool-mmrl-r36-hosted-official-debug.yml`. Require `BUILD SUCCESSFUL`,
positive native receipt, independent APK verification and both uploaded
artifacts. The workflow may expose Android plugin or CI environment blockers
that cannot be verified without running Gradle on GitHub.

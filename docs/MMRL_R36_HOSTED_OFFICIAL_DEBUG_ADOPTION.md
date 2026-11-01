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

## Acceptance

Source admission and offline tests are not hosted execution evidence.
After applying the overlay, push `master` and dispatch
`devtool-mmrl-r36-hosted-official-debug.yml`. Require `BUILD SUCCESSFUL`,
positive native receipt, independent APK verification and both uploaded
artifacts. The workflow may expose Android plugin or CI environment blockers
that cannot be verified without running Gradle on GitHub.

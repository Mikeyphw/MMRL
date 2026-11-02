# Phase 04 — Actual Android device instrumentation (read-only)

## Why a second APK?

The AndroidTest APK targets `com.dergoogler.mmrl.debug` and runs *on the device*, not on the CI runner. The existing verified OfficialDebug APK and the new instrumentation APK are built in **one GitHub Actions job** with one signing authority. The CI-generated receipt binds their APK hashes, package names, runner, GitHub run/attempt, and source SHA. No local Gradle invocation is needed.

## What is checked

- Android device and app identity, app launch, readable installed-Xposed-APK inventory.
- A best-effort UIAutomator navigation from Modules to LSPosed / Vector; onboarding and differing layouts yield `INCONCLUSIVE_UI`, not a fabricated UI pass.
- Existing provider liveness (no forced root grant).
- For `zygisk_vector`, the existing **read-only** `LsposedRuntime.inspect()` API: installed/boot-enabled state, Vector CLI daemon response and configured module list.
- JSON evidence written into the debuggable MMRL app's **own** private files and extracted with `adb exec-out run-as` (never world-readable dumps).

## What is **not** checked

- Hook execution in a target process, system_server injection, Zygisk load evidence, or real-world scope effects.
- No module installation, daemon writes, toggles, scope changes, reboots or root policy changes.
- No claim of device qualification from Gradle/build/signature/source-only success alone.
- The receipt and APK pair do not independently authenticate the GitHub artifact, and GitHub private/debug signing is not a release-signing authority.

## Result vocabulary

- `PASS_VECTOR_DAEMON_AND_MODULE_QUERY`: Vector's CLI status and module configuration both responded. **NOT proof hooks executed**.
- `INCONCLUSIVE_PROVIDER_UNAVAILABLE`: app's existing provider could not be contacted, e.g. initial setup incomplete or root not granted.
- `INCONCLUSIVE_DAEMON_UNVERIFIED`: root framework module exists but CLI is not confirmed.
- `INCONCLUSIVE_VECTOR_DISABLED`: module reported disabled for next boot.
- `INCONCLUSIVE_MODULE_QUERY`: daemon status answered but modules list was unavailable.
- `NOT_APPLICABLE_VECTOR_ABSENT`: no Vector root-module entry; legacy LSPosed requires separate protocol.
- `FAIL_TEST_HARNESS`: an unexpected instrumentation/test error.

A completed AndroidJUnitRunner test (`OK (1 test)`) only validates the harness; external acceptance classification MUST read the JSON status.

## Running

Follow the top-level README and `scripts/mmrl_lsposed_device_runner.py`. Select an explicit physical ADB device serial; emulator identifiers are rejected. The runner never performs uninstall or clears app data. If Android refuses to install due to an existing different signing certificate, stop and preserve existing data rather than force-uninstalling.

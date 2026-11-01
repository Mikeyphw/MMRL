# LSPosed / Vector Phase 02 — read-only discovery

Upstream-based MMRL fork, **not** legacy `master`. Phase 01 CI is merged.

## Scope

- New **LSPosed / Vector** action from Modules toolbar navigates to a separate screen.
- Asynchronous, non-privileged Android `PackageManager` APK inventory (legacy `xposedmodule` meta-data and/or `assets/xposed_init`, modern `META-INF/xposed/java_init.list` / `native_init.list`).
- Recognized standalone managers can be launched with normal Android `getLaunchIntentForPackage`, on explicit click only.
- Vector's manager is often parasitic and opened through a notification; a missing standalone manager APK is *not* proof of absence.
- User-triggered, read-only inventory from MMRL's existing `PlatformManager.moduleManager.modules` (no new `su` command, no root escalation). Show available module id, version and installed-state enum; known Vector/LSPosed names are heuristics, not boot/runtime verification.
- Does not infer whether Zygisk is currently enabled, hooks active, Xposed scope enabled or Manager IPC available.

## Limitations & gates

- Package detection may miss split-only, invisible, obfuscated or unreadable packages.
- Scan is on `Dispatchers.IO`; package list only starts after the screen opens. Root module inventory only starts on user request.
- Existing external terminal/WebUI compatibility modules remain unqualified for production parity.
- Phase 02 is a reviewable **debug-only** PR. Debug CI builds through Devtool native DAG; no local Gradle. Do not treat successful build as device acceptance.
- Existing phase 01 source-controlled Lint baseline remains strict. Any new findings require explicit analysis; do not silently widen it or disable checks.

## Acceptance checks

1. Hosted DAG, APK signature/package/receipt, Lint baseline and six static contracts pass.
2. On-device: Modules → LSPosed / Vector opens the screen; APK scan does not trigger root; unavailable module provider is explained clearly; root inventory is only read upon tapping Inspect; manager launch works for separately installed manager apps.
3. Device acceptance (future): test Modern + Legacy Xposed APKs and Vector's parasitic manager on a real supported rooted device.

## Next phase

Phase 03: provider-specific *runtime* discovery and non-mutating manager IPC / status (after inspecting authoritative Vector/LSPosed APIs and testing on-device). Only then consider guarded installs/updates and reversible scope writes in subsequent phases.

References: https://github.com/JingMatrix/Vector and https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API

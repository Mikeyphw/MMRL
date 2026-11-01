# MMRL R37 OfficialDebug paired APKs — non-release

This change does **not** replace existing R36 or the MMRL release seal.

`mmrl-r37-paired-device-apks.yml` runs only on GitHub-hosted Linux after `workflow_dispatch`:

1. Checks out the exact workflow SHA.
2. Reuses pinned Devtool R36 hosted native DAG `plan/run/verify` to build/verify OfficialDebug.
3. Uses GitHub Gradle to assemble `:app:assembleOfficialDebugAndroidTest` in the *same run and checkout*.
4. Checks both APK package IDs, signer fingerprints, signature validity, and the instrumentation runner's target package, without claiming device execution.
5. Emits `mmrl.r37.hosted-pair.v1` with `test_execution: NOT_RUN`, source SHA, run/attempt, R36 native receipt digest identity, and APK hashes; uploads **three separate** app/test/evidence archives.

Application: `com.mikeyphw.mmrl.debug`. Test: `com.mikeyphw.mmrl.debug.test`.
Both APKs are debug-only and expected to share the same GitHub-run debug signing certificate.

The new read-only smoke class is `com.dergoogler.mmrl.acceptance.OfficialDebugAcceptanceTest`, with three tests: installed target package, launcher resolution, and launcher activity resumed without immediate finish. It never toggles modules, writes LSPosed databases, or executes root commands. CI assembling/compiling the suite is **not** real-device acceptance.

## Acceptance continuation

See Devtool `docs/R37_MMRL_DEVICE_ACCEPTANCE.md` in the companion overlay for authenticated import and the `--skip-build` AOPS03 invocation. Do not invoke `connectedOfficialDebugAndroidTest` locally. Do not consider build success, a signing fingerprint, or a manually launched app sufficient for device qualification.

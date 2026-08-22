# MMRL final release seal

O11 is the final remediation overlay. There is no O12 in this campaign.

## Required gates

The release seal must run from a clean checkout and execute:

- `python3 scripts/validate-mmrl-source-hygiene.py`
- `devtool -r . validate --task verifyMmrlStandaloneProductCleanup`
- `./gradlew :platform:testDebugUnitTest`
- `./gradlew :platform:testNativeContracts`
- `./gradlew :app:testOfficialDebugUnitTest`
- `./gradlew :app:lintOfficialDebug -Pmmrl.fullLint=true`
- `./gradlew :app:assembleOfficialDebug`
- `./gradlew :app:assembleOfficialRelease`
- `MMRL_RUN_CONNECTED_TESTS=1 scripts/run-mmrl-release-seal.sh` when an Android device or emulator is available.

## What this seals

- Personal-use official debug and signed release variants are release-gated; store-distribution build lanes are not part of this fork.
- Full lint is fatal only in the final seal path, preserving fast intermediate developer lint.
- Gradle wrapper downloads are checksum-pinned.
- Generated Devtool and APK/AAB/AAR outputs are excluded from source snapshots.
- Source archive naming matches its zstd compressor.
- Instrumentation contracts cover installed manifest/exported surfaces, boot receivers, FileProvider/content-URI grants and WorkManager/lifecycle service wiring.
- Platform JVM and native tests cover JNI/filesystem contract boundaries.
- BH64 is represented by the final release gate and the clean-checkout CI workflow.


Focused bug-hunt v4 removes obsolete store-build backup configs and stale embedded-Ash compatibility residue.

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

run_devtool_validate() {
  devtool --copy -r "$ROOT_DIR" validate \
    --memory-guard-mb 0 \
    --gradle-jvm-heap-mb "${MMRL_GRADLE_HEAP_MB:-768}" \
    --cpu-limit "${MMRL_CPU_LIMIT:-1}" \
    "$@"
}

run_devtool_full_lint_validate() {
  env 'ORG_GRADLE_PROJECT_mmrl.fullLint=true' \
    devtool --copy -r "$ROOT_DIR" validate \
      --memory-guard-mb 0 \
      --gradle-jvm-heap-mb "${MMRL_LINT_HEAP_MB:-640}" \
      --cpu-limit "${MMRL_CPU_LIMIT:-1}" \
      "$@"
}

printf 'mmrl-release-seal: source and repository hygiene\n'
python3 scripts/validate-mmrl-source-hygiene.py
run_devtool_validate \
  --task 'verifyStableToolchainBaseline' \
  --task 'verifyMmrlProductBoundary' \
  --task 'verifyRepositoryHygiene'

printf 'mmrl-release-seal: JVM/native/unit contracts\n'
run_devtool_validate \
  --task ':platform:testNativeContracts' \
  --task ':platform:testDebugUnitTest' \
  --task ':app:testOfficialDebugUnitTest'

printf 'mmrl-release-seal: Android compile/resource/instrumentation compilation\n'
run_devtool_validate \
  --task ':app:compileOfficialDebugSources' \
  --task ':app:processOfficialDebugResources' \
  --task ':app:compileOfficialDebugAndroidTestKotlin'

printf 'mmrl-release-seal: strict full lint\n'
run_devtool_full_lint_validate --task ':app:fullLintOfficialDebug'

printf 'mmrl-release-seal: official debug/release artifacts\n'
run_devtool_validate \
  --task ':app:assembleOfficialDebug' \
  --task ':app:assembleOfficialRelease' \
  --task ':app:verifyReleaseArtifacts'

printf 'mmrl-release-seal: optional connected-device validation\n'
MMRL_RUN_CONNECTED_TESTS="${MMRL_RUN_CONNECTED_TESTS:-auto}" \
  scripts/run-mmrl-device-validation.sh

printf 'mmrl-release-seal: PASS\n'

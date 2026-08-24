#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

run_devtool_validate() {
  devtool -r "$ROOT_DIR" validate "$@"
}

run_devtool_full_lint_validate() {
  env 'ORG_GRADLE_PROJECT_mmrl.fullLint=true' \
    devtool -r "$ROOT_DIR" validate "$@"
}

printf 'mmrl-release-seal: source hygiene\n'
python3 scripts/validate-mmrl-source-hygiene.py

printf 'mmrl-release-seal: toolchain and product boundary gates\n'
run_devtool_validate --task 'verifyStableToolchainBaseline' --task 'verifyMmrlProductBoundary'

printf 'mmrl-release-seal: JVM, native, and variant assembly via devtool validator\n'
run_devtool_validate \
  --task ':platform:testNativeContracts' \
  --task ':platform:testDebugUnitTest' \
  --task ':app:testOfficialDebugUnitTest' \
  --task ':app:compileOfficialDebugAndroidTestKotlin' \
  --task ':app:assembleOfficialDebug' \
  --task ':app:assembleOfficialRelease' \
  --task ':app:verifyReleaseArtifacts'

printf 'mmrl-release-seal: full lint via devtool validator\n'
run_devtool_full_lint_validate \
  --task ':app:lintOfficialDebug'

if [[ "${MMRL_RUN_CONNECTED_TESTS:-0}" == "1" ]]; then
  printf 'mmrl-release-seal: connected Android instrumentation via devtool validator\n'
  run_devtool_validate \
    --task ':app:connectedOfficialDebugAndroidTest'
else
  printf 'mmrl-release-seal: connected Android instrumentation skipped; set MMRL_RUN_CONNECTED_TESTS=1 to require a device/emulator.\n'
fi

printf 'mmrl-release-seal: PASS\n'

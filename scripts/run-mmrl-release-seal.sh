#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

run_devtool_validate() {
  devtool -r "$ROOT_DIR" validate "$@"
}

printf 'mmrl-release-seal: source hygiene\n'
python3 scripts/validate-mmrl-source-hygiene.py

printf 'mmrl-release-seal: AshReXcue static gate\n'
bash scripts/validate-ashrexcue-release.sh --static-only

printf 'mmrl-release-seal: JVM, native, lint, and variant assembly via devtool validator\n'
run_devtool_validate \
  --gradle-arg '-Pmmrl.fullLint=true' \
  --task ':platform:testNativeContracts' \
  --task ':platform:testDebugUnitTest' \
  --task ':app:testOfficialDebugUnitTest' \
  --task ':app:lintOfficialDebug' \
  --task ':app:assembleOfficialDebug' \
  --task ':app:assembleOfficialRelease' \
  --task ':app:assembleOfficialPlaystore'

if [[ "${MMRL_RUN_CONNECTED_TESTS:-0}" == "1" ]]; then
  printf 'mmrl-release-seal: connected Android instrumentation via devtool validator\n'
  run_devtool_validate \
    --task ':app:connectedOfficialDebugAndroidTest'
else
  printf 'mmrl-release-seal: connected Android instrumentation skipped; set MMRL_RUN_CONNECTED_TESTS=1 to require a device/emulator.\n'
fi

printf 'mmrl-release-seal: PASS\n'

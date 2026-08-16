#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MAX_WORKERS="${GRADLE_MAX_WORKERS:-2}"
GRADLE=("$ROOT_DIR/gradlew" --max-workers="$MAX_WORKERS" --build-cache --console=plain)

printf 'mmrl-release-seal: source hygiene\n'
python3 scripts/validate-mmrl-source-hygiene.py

printf 'mmrl-release-seal: AshReXcue static gate\n'
bash scripts/validate-ashrexcue-release.sh --static-only

printf 'mmrl-release-seal: JVM, native, lint, and variant assembly\n'
"${GRADLE[@]}" \
  :platform:testNativeContracts \
  :platform:testDebugUnitTest \
  :app:testOfficialDebugUnitTest \
  :app:lintOfficialDebug -Pmmrl.fullLint=true \
  :app:assembleOfficialDebug \
  :app:assembleOfficialRelease \
  :app:assembleOfficialPlaystore

if [[ "${MMRL_RUN_CONNECTED_TESTS:-0}" == "1" ]]; then
  printf 'mmrl-release-seal: connected Android instrumentation\n'
  "${GRADLE[@]}" :app:connectedOfficialDebugAndroidTest
else
  printf 'mmrl-release-seal: connected Android instrumentation skipped; set MMRL_RUN_CONNECTED_TESTS=1 to require a device/emulator.\n'
fi

printf 'mmrl-release-seal: PASS\n'

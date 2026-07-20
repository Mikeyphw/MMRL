#!/usr/bin/env bash
set -euo pipefail

MODE="focused"
case "${1:-}" in
  "") ;;
  --static-only) MODE="static" ;;
  --focused) MODE="focused" ;;
  --with-apk) MODE="apk" ;;
  -h|--help)
    cat <<'USAGE'
Usage: scripts/validate-ashrexcue-release.sh [--static-only|--focused|--with-apk]

  --static-only  Run source, schema, shell, protocol, and module self-tests only.
  --focused      Run the final AshReXcue Android task set without full APK/native assembly.
  --with-apk     Also assemble the official debug APK. Requires a host-compatible Android NDK.
USAGE
    exit 0
    ;;
  *)
    echo "Unknown option: $1" >&2
    exit 2
    ;;
esac

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$REPO_ROOT"

fail() {
  echo "release-gate: $*" >&2
  exit 1
}

pass() {
  printf 'release-gate: %-42s PASS\n' "$1"
}

MODULE_DIR="app/src/main/ash-module"
MODULE_PROP="$MODULE_DIR/module.prop"
AIDL_FILE="app/src/main/aidl/com/dergoogler/mmrl/ash/root/IAshReXcueService.aidl"

[[ -f "$MODULE_PROP" ]] || fail "missing $MODULE_PROP"
[[ "$(sed -n 's/^version=//p' "$MODULE_PROP")" == "11.6.0" ]] || fail "module version is not 11.6.0"
[[ "$(sed -n 's/^versionCode=//p' "$MODULE_PROP")" == "260" ]] || fail "module versionCode is not 260"
pass "module release metadata"

for script in ashrexcuectl action.sh customize.sh post-fs-data.sh service.sh uninstall.sh utils.sh external-control.sh; do
  [[ -x "$MODULE_DIR/$script" ]] || fail "$MODULE_DIR/$script is not executable"
  sh -n "$MODULE_DIR/$script" || fail "$MODULE_DIR/$script failed shell syntax"
done
pass "module shell syntax and permissions"

command -v jq >/dev/null 2>&1 || fail "jq is required for the release self-test"
state_dir=$(mktemp -d)
modules_dir=$(mktemp -d)
trap 'rm -rf "$state_dir" "$modules_dir"' EXIT
release_json=$(ASH_TEST_STATE_DIR="$state_dir" \
  ASH_TEST_MODULES_DIR="$modules_dir" \
  ASH_TEST_JQ="$(command -v jq)" \
  ASH_TEST_NO_SYNC=1 \
  sh "$MODULE_DIR/ashrexcuectl" release-gate)
printf '%s' "$release_json" | jq -e '
  .ok == true and
  .protocolVersion == "ashrexcue.release.v1" and
  .status == "ready" and
  ([.checks[] | select(.state == "blocker")] | length) == 0
' >/dev/null || fail "embedded module release gate did not pass"
pass "embedded module release self-test"

python3 - <<'PY'
import json
from pathlib import Path

schemas = sorted(Path("app/schemas").rglob("*.json"))
if len(schemas) != 18:
    raise SystemExit(f"expected 18 Room schemas, found {len(schemas)}")
for path in schemas:
    data = json.loads(path.read_text(encoding="utf-8"))
    if set(data) != {"formatVersion", "database"}:
        raise SystemExit(f"unexpected Room schema keys in {path}: {sorted(data)}")
    expected = int(path.stem)
    actual = data.get("database", {}).get("version")
    if actual != expected:
        raise SystemExit(f"Room schema version mismatch in {path}: {actual} != {expected}")

for base in (Path("app/src/main/kotlin/com/dergoogler/mmrl/ash"), Path("app/src/test/kotlin/com/dergoogler/mmrl/ash")):
    for path in base.rglob("*"):
        if not path.is_file():
            continue
        raw = path.read_bytes()
        bad = [byte for byte in raw if byte < 32 and byte not in (9, 10, 13)]
        if bad:
            raise SystemExit(f"hidden control byte in {path}")
PY
pass "Room schemas and control-character scan"

[[ -f "$AIDL_FILE" ]] || fail "missing typed AshReXcue AIDL"
grep -q 'String releaseGate();' "$AIDL_FILE" || fail "releaseGate is missing from typed AIDL"
if grep -Eq 'String[[:space:]]+(exec|executeShell|shell|runCommand|command)[[:space:]]*\(' "$AIDL_FILE"; then
  fail "generic command execution is exposed through AIDL"
fi
pass "typed AIDL security surface"

grep -q 'release-gate-v1' "$MODULE_DIR/ashrexcuectl" || fail "release capability is missing"
if grep -RIn --include='*.kt' 'kotlin\.test' app/src/test >/dev/null; then
  fail "Android tests must use JUnit rather than kotlin.test"
fi
pass "release protocol and JUnit policy"

if [[ "$MODE" == "static" ]]; then
  echo "release-gate: static release validation complete"
  exit 0
fi

[[ -x ./gradlew ]] || fail "Gradle wrapper is missing or not executable"

tasks=(
  :app:packageAshReXcueModule
  :app:compileOfficialDebugAidl
  :app:kspOfficialDebugKotlin
  :app:compileOfficialDebugKotlin
  :app:compileOfficialDebugJavaWithJavac
  :app:testOfficialDebugUnitTest
  :app:lintOfficialDebug
)
if [[ "$MODE" == "apk" ]]; then
  tasks+=( :app:assembleOfficialDebug )
fi

./gradlew \
  "${tasks[@]}" \
  --max-workers="${GRADLE_MAX_WORKERS:-2}" \
  --build-cache

pass "focused Android release tasks"
echo "release-gate: AshReXcue Phase J validation complete"

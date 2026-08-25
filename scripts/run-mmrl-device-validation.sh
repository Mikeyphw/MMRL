#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mode="${MMRL_RUN_CONNECTED_TESTS:-auto}"
requested_serial="${MMRL_ADB_SERIAL:-${ANDROID_SERIAL:-}}"

case "$mode" in
  0|false|no|off)
    printf 'mmrl-device-validation: skipped by MMRL_RUN_CONNECTED_TESTS=%s\n' "$mode"
    exit 0
    ;;
  1|true|yes|on|auto) ;;
  *)
    printf 'mmrl-device-validation: invalid MMRL_RUN_CONNECTED_TESTS=%s (expected auto, 0, or 1)\n' "$mode" >&2
    exit 2
    ;;
esac

required=0
case "$mode" in 1|true|yes|on) required=1 ;; esac

if ! command -v adb >/dev/null 2>&1; then
  if (( required )); then
    printf 'mmrl-device-validation: adb is required but not available\n' >&2
    exit 1
  fi
  printf 'mmrl-device-validation: no adb; connected validation skipped\n'
  exit 0
fi

mapfile -t devices < <(adb devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 }')

serial=""
if [[ -n "$requested_serial" ]]; then
  for candidate in "${devices[@]:-}"; do
    if [[ "$candidate" == "$requested_serial" ]]; then serial="$candidate"; break; fi
  done
  if [[ -z "$serial" ]]; then
    if (( required )); then
      printf 'mmrl-device-validation: requested device %s is not in adb device state\n' "$requested_serial" >&2
      exit 1
    fi
    printf 'mmrl-device-validation: requested device %s unavailable; skipped\n' "$requested_serial"
    exit 0
  fi
elif (( ${#devices[@]} == 1 )); then
  serial="${devices[0]}"
elif (( ${#devices[@]} == 0 )); then
  if (( required )); then
    printf 'mmrl-device-validation: no authorized adb device is connected\n' >&2
    exit 1
  fi
  printf 'mmrl-device-validation: no authorized adb device; skipped\n'
  exit 0
else
  if (( required )); then
    printf 'mmrl-device-validation: multiple adb devices are connected; set MMRL_ADB_SERIAL\n' >&2
    printf '  %s\n' "${devices[@]}" >&2
    exit 1
  fi
  printf 'mmrl-device-validation: multiple adb devices; set MMRL_ADB_SERIAL to select one; skipped\n'
  exit 0
fi

printf 'mmrl-device-validation: using %s\n' "$serial"
export ANDROID_SERIAL="$serial"

# Direct Gradle is intentional here: device selection was already validated above, so a
# Devtool adb-state parser problem cannot turn a usable device into a false skip.
./gradlew :app:connectedOfficialDebugAndroidTest \
  --max-workers=1 \
  --no-daemon \
  -Dorg.gradle.parallel=false \
  -Dorg.gradle.vfs.watch=false \
  -Pkotlin.compiler.execution.strategy=in-process \
  --console=plain

# Root is optional. Probe it only when su actually yields uid 0; do not change device state.
root_id="$(adb -s "$serial" shell 'su -c id' 2>/dev/null | tr -d '\r' || true)"
if [[ "$root_id" == *'uid=0'* ]]; then
  printf 'mmrl-device-validation: optional root smoke PASS (%s)\n' "$root_id"
else
  printf 'mmrl-device-validation: root unavailable; root smoke skipped\n'
fi

printf 'mmrl-device-validation: PASS\n'

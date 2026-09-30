#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Keep only the historical serial convenience. Devtool AOPS02 owns discovery,
# authorization/state checks, device policy, leasing, install/run and evidence.
if [[ -n "${MMRL_ADB_SERIAL:-}" ]]; then
  export ANDROID_SERIAL="$MMRL_ADB_SERIAL"
fi

DEVTOOL_BIN="${DEVTOOL_BIN:-devtool}"
if ! command -v "$DEVTOOL_BIN" >/dev/null 2>&1 && [[ ! -x "$DEVTOOL_BIN" ]]; then
  printf 'mmrl-device-validation: Devtool runtime not found: %s\n' "$DEVTOOL_BIN" >&2
  exit 127
fi

exec "$DEVTOOL_BIN" --repo "$ROOT_DIR" --target mmrl_android instrumentation "$@"

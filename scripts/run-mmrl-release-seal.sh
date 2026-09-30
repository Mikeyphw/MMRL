#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

DEVTOOL_BIN="${DEVTOOL_BIN:-devtool}"
if ! command -v "$DEVTOOL_BIN" >/dev/null 2>&1 && [[ ! -x "$DEVTOOL_BIN" ]]; then
  printf 'mmrl-release-seal: Devtool runtime not found: %s\n' "$DEVTOOL_BIN" >&2
  exit 127
fi

# Wrapper schema 2 currently does not expose `release` as a wrapper-native
# command. Do not emulate it with a nested Devtool workflow: enter AOPS04
# directly so release evidence, signing verification, device policy and smoke
# gates all share one canonical run identity.
exec "$DEVTOOL_BIN" --repo "$ROOT_DIR" --target mmrl_android release "$@"

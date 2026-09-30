#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

FLAVOR="${1:-official}"
BUILD_TYPE="${2:-all}"

if [[ "$FLAVOR" != "official" ]]; then
  printf 'MMRL canonical release tooling supports only the official flavor (got %s)\n' "$FLAVOR" >&2
  exit 2
fi

case "$BUILD_TYPE" in
  all)
    exec "$ROOT_DIR/devtoolw" release
    ;;
  debug)
    exec "$ROOT_DIR/devtoolw" validate --task ':app:assembleOfficialDebug'
    ;;
  release)
    exec "$ROOT_DIR/devtoolw" validate --task ':app:assembleOfficialRelease' --task ':app:verifyReleaseArtifacts'
    ;;
  *)
    printf 'Unsupported build type: %s (expected debug, release, or all)\n' "$BUILD_TYPE" >&2
    exit 2
    ;;
esac

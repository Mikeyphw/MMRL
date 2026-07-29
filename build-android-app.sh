#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../build-android-lib.sh"

DEFAULT_OUTPUT_NAME="mmrl-android"
parse_output_dir "$@"
build_android_target "$SCRIPT_DIR" "mmrl_android" "$OUTPUT_DIR"

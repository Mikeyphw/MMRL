#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

FLAVOR="${1:-official}"
BUILD_TYPE="${2:-all}"

variant_name() {
  local flavor="$1"
  local build_type="$2"
  printf '%s%s' \
    "$(tr '[:lower:]' '[:upper:]' <<<"${flavor:0:1}")${flavor:1}" \
    "$(tr '[:lower:]' '[:upper:]' <<<"${build_type:0:1}")${build_type:1}"
}

latest_apk() {
  local output_dir="$1"
  find "$output_dir" -maxdepth 1 -type f -name '*.apk' -printf '%T@ %p\n' \
    | sort -nr \
    | awk 'NR == 1 { sub(/^[^ ]+ /, ""); print }'
}

build_variant() {
  local flavor="$1"
  local build_type="$2"
  local variant task output_dir apk

  variant="$(variant_name "$flavor" "$build_type")"
  task=":app:assemble${variant}"
  output_dir="app/build/outputs/apk/${flavor}/${build_type}"

  echo "Building ${task}..."
  "$ROOT_DIR/gradlew" "$task" --no-daemon --console=plain

  apk="$(latest_apk "$output_dir")"
  if [[ -z "${apk:-}" ]]; then
    echo "No APK found in ${output_dir}" >&2
    exit 1
  fi

  echo "Built ${build_type} APK:"
  echo "$apk"
}

if [[ "$BUILD_TYPE" == "all" ]]; then
  build_variant "$FLAVOR" debug
  build_variant "$FLAVOR" release
else
  build_variant "$FLAVOR" "$BUILD_TYPE"
fi

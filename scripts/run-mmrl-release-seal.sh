#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Canonical repository ingress. The release-seal DAG, resource policy, EXO/TLJ
# provenance, and compatibility boundaries are owned by .devtool.toml.
exec "$ROOT_DIR/devtoolw" release

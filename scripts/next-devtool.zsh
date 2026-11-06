#!/usr/bin/env zsh
# Read-only local Devtool helper. All Android Gradle execution stays on GitHub.
setopt ERR_EXIT PIPE_FAIL

repo="${0:A:h:h}"
if ! command -v devtool >/dev/null 2>&1; then
  print -u2 'N03_REFUSED: devtool executable not found in PATH'
  exit 2
fi
case "${1:-}" in
  state) shift; exec devtool -r "$repo" git state "$@" ;;
  doctor) shift; exec devtool -r "$repo" git doctor "$@" ;;
  config) shift; exec devtool -r "$repo" config resolved "$@" ;;
  actions) shift; exec devtool -r "$repo" actions workflows --github-repo Mikeyphw/MMRL "$@" ;;
  *)
    print -u2 'Usage: scripts/next-devtool.zsh {state|doctor|config|actions} [read-only flags]'
    print -u2 'Native Gradle jobs run exclusively in GitHub Actions: .github/workflows/next-devtool-native.yml'
    exit 2 ;;
esac

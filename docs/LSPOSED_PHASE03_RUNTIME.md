# Phase 03: bounded read-only runtime diagnostics

Base: MMRL `lsposed-vector` merge `7e5cbff4fa57a04efbc24b62e90456433a52d22e`.

## Sources and evidence

Vector officially ships `zygisk/module/cli`, which accepts `--json status` and `--json modules ls` and talks to its daemon via authenticated socket; the CLI itself requires root. The `status` JSON reports framework version, version code, Xposed API version, and the count of configured enabled modules; `modules ls` returns package and enabled/disabled state. Source: https://github.com/JingMatrix/Vector/blob/master/daemon/src/main/kotlin/org/matrix/vector/daemon/Cli.kt and `daemon/.../ipc/CliHandler.kt`.

The MMRL app only makes these calls after a user explicitly taps **Check Vector runtime (root)** on the existing LSPosed/Vector discovery screen. The button uses a bounded `su -c` execution of the exact installed `/data/adb/modules/zygisk_vector/cli` shell script, only when the existing MMRL provider lists `zygisk_vector` and its module state is `ENABLE`. No background probing, startup root request, Zygisk alteration or scope writes. Runs on Dispatchers.IO with max 10s per command and 128 KiB output cap; parsing is fail-closed. Root approval dialogs may appear; a refusal or timeout yields **not verified**, never **inactive**.

## Distinguish signals

1. Module **installed**: root module manager inventory lists `zygisk_vector` (not proof of loaded Zygisk).
2. Module **enabled**: root module manager state is `ENABLE` (not proof of a healthy boot).
3. **Daemon responded**: CLI `--json status` returned a validated successful response (stronger evidence; still not proof of injection).
4. **Configured Xposed modules**: daemon `modules ls` lists them `enabled` or `disabled` (configuration, not actual target-process activation).
5. **Zygisk injected into system_server, target process or hooks executing**: **UNKNOWN**. Vector CLI's `status` endpoint does not expose this evidence; do not claim it.
6. **Legacy LSPosed**: installed module only; no stable protocol is available through this implementation, so daemon/hook status remains unknown.

## Compatibility limits

The `zygisk_vector/cli` path corresponds to Vector's standard module id; alternative relocated module layouts (e.g. Magisk's internal mirror), missing root access, or older Vector builds will produce **unverified** rather than false negatives. The `--json` schema is based on Vector's public CLI. Future Vector releases may evolve it; errors remain explicit. The CLI calls are opt-in and execute the installed root-module script rather than a bundled binary.

## CI & acceptance

- Python-only source contracts (`test_mmrl_lsposed_phase03_contract.py`), full Devtool OfficialDebug compile/signature/provenance and strict Lint baseline run on GitHub Actions. Never run Gradle in Termux.
- On device, verify: without provider => unavailable; Vector absent => no root CLI invocation; disabled => no CLI; `su` denied / command timeout => unknown; Vector healthy => version/API from daemon, module configuration list; at no time claim active hooks. Validate user-requested root interaction and normal navigation/back behavior.
- This is **Phase 03 read-only**, not live hook telemetry or Xposed scope management. Do not merge based only on source tests; require hosted CI and record device tests separately.

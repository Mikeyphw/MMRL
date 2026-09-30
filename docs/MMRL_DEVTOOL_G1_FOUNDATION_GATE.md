# MMRL-DT-G1 — Canonical Devtool Foundation Gate

MMRL-DT-G1 qualifies the Devtool foundation introduced by MMRL-DT-X01. It is deliberately a gate rather than another implementation wave: Android device/release migration remains owned by X02, and repository-wide CI/Mise/artifact/Gradle-takeover convergence remains owned by X03.

## Gate result contract

The executable gate is `scripts/validate-mmrl-devtool-foundation-gate.py`. The overlay's required validation runs this contract automatically. A successful gate proves:

- the repository config is already on the current Devtool schema and does not need migration;
- Android-safe validation is enabled and the typed Gradle policy remains wrapper-based, constrained, one-worker, non-parallel, no-daemon, VFS-off, Configuration-Cache-off, standard-telemetry, collect-failures;
- `devtoolw` and `devtoolw.cmd` are current, verified, sealed, documented, and expose exactly the intended local command surface;
- native wrapper commands resolve back into canonical Devtool commands for `mmrl_android`, while `release` and `device` resolve to the target-local workflow DAGs;
- all seven reusable jobs are target-local; no repository-global reusable job remains;
- `validate`, `device`, and `release-seal` resolve with the expected dependency topology and no scheduler error;
- every workflow node has a known write contract, source hygiene is genuinely read-only, Gradle work is classified `gradle-heavy`, and the compatibility device node is `device-exclusive` with the `mmrl-adb-device` reservation;
- Devtool's target-level preflight for `release-seal` is healthy on the native-Termux execution environment, including authoritative tool requirements;
- canonical first-party local/release scripts enter through `devtoolw` and do not invoke raw Gradle.

## Raw-Gradle ledger at G1

G1 intentionally does **not** pretend all raw Gradle has already disappeared. The gate classifies and constrains the remaining occurrences so that new bypasses fail closed:

- `scripts/run-mmrl-device-validation.sh` — temporary X02 compatibility boundary for the previously observed Devtool ADB/parser problem;
- `.github/workflows/mmrl-release-seal.yml` — X03 CI convergence debt;
- `mise.toml` — X03 developer-task convergence debt;
- `docs/MMRL_TOOLCHAIN_PRODUCT_BOUNDARY.md` — documentation example only.

No other raw `./gradlew` ingress is accepted by this gate.

## Audit-loop findings folded into G1

The first native-Termux G1 run exposed two X01 integration gaps plus one gate-diagnostics gap:

- `mise.toml` is a convenience task surface, but Devtool destination preflight treats a dedicated Mise marker as toolchain ownership in `auto` mode. MMRL now declares `[toolchain] manager = "none"` so canonical Devtool execution does not acquire an accidental Mise runtime dependency. X03 still owns convergence of the *tasks inside* `mise.toml` onto `devtoolw`.
- `scripts/run-mmrl-device-validation.sh` is intentionally mode `0644`, so the temporary `device-compat` job now invokes it via `bash` instead of pretending it is directly executable. X02 still owns removal of this compatibility boundary.
- G1 v1 inspected failing preflight `checks` but not failing `tools`, producing an opaque `bad_checks=[]` failure. The gate now treats both collections as authoritative and prints either class of failure.

The audit had also corrected the current CLI shape: workflow preflight is selected with `preflight --workflow release-seal`, while `workflows` target selection belongs at the global CLI layer.

With these remediations, the X01 execution model qualifies structurally: seven target-local jobs, explicit/known scheduler writes, read-only source hygiene, current wrapper launchers, clean workflow scheduling, and no accidental external toolchain-manager dependency.

## Next boundary

MMRL-DT-X02 owns Android Validation & Release Authority: persistent/resumable validation, native ADB and instrumentation, removal of the device compatibility raw-Gradle exception when qualified, Devtool Android release orchestration, project-signing integration, signature/certificate checks, smoke gates, and immutable release evidence.

# MMRL-DT-G2 — Android validation and release gate

MMRL-DT-G2 is the authoritative milestone gate for the X02 window after **MMRL-DT-G2-R01** has remediated validation ownership and native-Termux/bootstrap policy. G2 contains no Android implementation-policy changes; it qualifies the already-remediated production paths.

## Prerequisite

R01 must already be applied and pass its targeted validation. In particular, native `devtool validate` must own the seven-phase resumable Android pipeline; the repository EXO DAG must be `repository-check`; native-Termux/bootstrap selection must be explicit; and full release instrumentation must be `required`.

## Authoritative gate contract

Apply-time validation:

1. re-runs the complete targeted X02 and R01 structural/planning contracts;
2. verifies canonical config migration and wrapper sealing, the seven-phase native Android validation plan, the six-node `repository-check` EXO DAG, the native device EXO plan, the seven-node `release-check` DAG, and the AOPS04 phase graph;
3. on native Termux, uses an existing ignored project signer when available, otherwise creates a disposable gate-only validation signer outside the repository and removes all temporary material on every exit path;
4. executes one fresh persistent `devtool validate --sequential --restart-session --complete --require-complete` session with failure action `pause`;
5. executes one canonical AOPS04 `devtool release` with required instrumentation and explicit evidence export;
6. cryptographically verifies the immutable release-evidence seal and requires passed unit, lint, package, instrumentation, signature-verification, and install-smoke gates;
7. verifies project-signing identity, one canonical Android `DeviceLease`, the artifact pair, Android execution snapshot, and at least one APK artifact.

Upgrade-smoke remains enabled. When no previous APK is configured, AOPS04 must record it as a non-required `skipped` gate rather than pretending it ran.

## Runtime requirements

The authoritative gate intentionally fails outside native Termux. It also fails closed when the project signer is invalid and an ephemeral signer cannot be created, exactly one authorized device cannot be admitted, durable validation does not complete, instrumentation fails or crashes, signature/certificate verification fails, install smoke fails, or immutable evidence is incomplete or hash-invalid.

`MMRL_G2_STRUCTURAL_ONLY=1` exists only for construction-time replay in a non-Termux implementation environment. The packaged artifact does not set it.

## Roadmap position

G2 remains full-plan overlay **4 of 6**. R01 is an inserted remediation and does not advance the numerator. After G2 passes, the next implementation scope is **MMRL-DT-X03 — Repository-Wide Devtool Convergence**.

# MMRL-DT-G2-R01 — native-Termux validation ownership remediation

R01 is a remediation implementation overlay inserted after X02 and before the G2 milestone gate. It does not advance the six-overlay roadmap position.

It separates repository-owned EXO validation from Devtool native Android validation by renaming the repository DAG to `repository-check`, makes native validation resumable with seven owned phases, and reserves instrumentation for AOPS04. It also makes the native-Termux execution contract fail-closed (`prefer_native_termux = true`) and allows Devtool to reuse/provision only missing Android SDK components (`downloads = "missing"`, `network = "allow"`).

Full release instrumentation is now `required`, matching the already-required install-smoke device boundary. The explicit package-only release path remains the documented exception and disables its device gates intentionally.

## Validation boundary

R01 validation is targeted and side-effect-free: it validates source/config/wrapper contracts and Devtool plans only. It does not run Gradle, ADB instrumentation, signing, install smoke, or release publication. Those remain exclusively owned by MMRL-DT-G2.

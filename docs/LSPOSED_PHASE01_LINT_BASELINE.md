# Phase 01: independent APK and Android Lint DAG nodes

This integration branch uses two distinct, source-bound Devtool native Android jobs:
`node-01 = assemble_debug` and `node-02 = lint_debug`. Both invoke the exact
upstream Gradle 9.1/JDK 21 wrapper **via Devtool** on GitHub Actions. The DAG
has no dependency from APK assembly to Lint or from Lint to APK assembly.
The workflow runs Lint even if the APK task fails, provided DAG planning succeeded.

`node-01` produces the exact debug APK and an RM-06 immutable handoff receipt.
The existing verifier checks file SHA-256, source/graph/run lineage, certificate
and Android application ID. These checks are NOT release or device qualification.
The debug APK is uploaded immediately after verification even if Lint later fails.

`node-02` runs `:app:lintOfficialDebug`, keeping XML, HTML and text reports.
The upstream `:platform` workaround disables only `UElementAsPsi` in CI to avoid
its known analyzer crash. `abortOnError=false` is CI-only, intentionally allowing
report generation. It does **not** mean the Lint gate passed: an independent Python
validator fails when the native Lint job fails, the XML is malformed/missing, the
baseline is missing/tampered, any Lint analyzer error is present, or any new
error/warning/information finding appears above a previously approved count.

## Review/approve the existing Lint baseline (two-step acceptance)

The first CI run **intentionally fails** because there is no approved baseline.
It uploads `mmrl-lsposed-ci-evidence-<run>-attempt<attempt>` containing
`lint-candidate.json` and `lint-gate.json`, alongside full Android Lint reports
in a separate `mmrl-lsposed-lint-reports-*` artifact. This is a review gate,
not permission to suppress all preexisting errors automatically.

1. Download those artifacts for the PR run. Inspect the actual XML/HTML report,
   its error counts, severe findings and candidate JSON. Resolve any critical
   findings immediately rather than adding them to a blanket exception list.
2. In Termux, with the candidate available locally, explicitly approve the
   remaining findings. Example (substitute actual artifact path and your review
   identity and rationale):

   ```zsh
   python3 scripts/mmrl_lsposed_lint_gate.py approve \
     --candidate "$HOME/storage/downloads/lint-candidate.json" \
     --output ci/mmrl-lsposed-lint-baseline.json \
     --reviewer "Mikeyphw" \
     --review-note "Reviewed the full Lint report; tracking existing findings"
   git add ci/mmrl-lsposed-lint-baseline.json
   git commit -m "ci(mmrl): approve reviewed upstream lint debt baseline"
   git push origin feature/lsposed-ci-baseline
   ```

   The `approve` operation refuses to overwrite an existing baseline. To replace
   it, review the changes and explicitly remove the old baseline before running.
3. The next CI run compares normalized issue ID, severity, source path, message
   and occurrence count. New findings **fail**; fixes/removed issues pass, with
   resolved counts reported. The line number is excluded so line shifts don't
   create false regressions. A new occurrence of an identical finding in the
   same file still fails because the occurrence count increases.

## Limitations and follow-up

- A baseline is a temporary debt inventory, not proof that all existing Lint
  findings are acceptable; track and resolve issues individually.
- No release signing or device-test entitlement is granted by this CI path.
- Lint report files are intentionally not included in Devtool's declared
  generated-output handoff; they are untrusted diagnostics uploaded separately.
- The repository includes local compatibility implementations of private
  `dev.mmrlx` dependencies; passing CI does not prove their feature parity.
- The initial integration package name remains `com.dergoogler.mmrl.debug`.
  Isolated package identity is a subsequent feature/release decision.

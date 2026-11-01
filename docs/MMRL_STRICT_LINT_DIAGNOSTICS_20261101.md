# MMRL strict OfficialDebug Lint — evidence-first remediation

## Trigger

[MMRL release-seal run 41266150159](https://github.com/Mikeyphw/MMRL/actions/runs/41266150159)
on commit `eab8ff6c05cab09a4c8087c13cdf4c7a85bce266`: `static`, `release`
and instrumentation compilation succeeded. The `debug` lane's unit test task
completed; `:app:lintOfficialDebug -Pmmrl.fullLint=true` failed with **22 errors**
and 5 hints. Previous logs expose only the first error and the workflow did not
upload lint reports, so the other 21 findings are not yet independently known.

## Known first finding and bounded correction

`platform/.../file/PrivilegedPathPolicy.kt` includes `/sdcard` in both approved
read and mutation roots for a root-capable file manager. Replacing this literal
with app-context storage APIs would alter the caller-visible privileged path
policy, and changing that allowlist without full security review would be unsafe.
Therefore the `SdCardPath` warning is suppressed **only** on these two existing
allowlist declarations with a source comment explaining the deliberate alias.
Symlink, traversal, and descriptor pseudo-path checks are preserved unchanged.
No global lint rule is disabled and no baseline is introduced.

## GitHub-only next evidence step

On the next `master` push, the existing release-seal matrix retains its strict
full-lint fatal gate. Regardless of the debug lane result, it now:

1. Prints the complete app lint text report into the job log.
2. Uploads available text, SARIF, HTML lint reports and debug JUnit reports as
   `mmrl-strict-lint-<run id>-attempt<attempt>` (14-day retention).

Inspect all distinct reported file/line/issue IDs, fix source bugs in controlled
patches, and rerun the workflow until the debug lane passes. Do not infer the
remaining 21 issues from only the first console line. **Release and device
qualification stay separate** from GitHub-hosted debug or signing-validation jobs.

The source-only guard is `python3 tests/test_mmrl_strict_lint_ci.py`; it never
executes Gradle. The authoritative strict lint execution is GitHub Actions only.

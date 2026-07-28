# Debug Workbench

The Debug Workbench is a read-only troubleshooting surface for MMRL support cases that previously required manual logcat inspection.

Phase 1/2 introduces:

- shared `DebugProbeResult` models and redaction helpers;
- a settings developer entry point;
- a `DebugWorkbenchScreen` with **Run read-only probes** and **Copy redacted report** actions;
- LSPosed / Vector manager package visibility checks;
- LSPosed / Vector provider module scans under `/data/adb/modules` and `/data/adb/modules_update`;
- LSPosed repository endpoint matrix checks for `modules.lsposed.org`, `backup.modules.lsposed.org`, and the generated jsDelivr mirror;
- GitHub token storage status without exporting the token;
- AshReXcue alias and module-file evidence.

The workbench must not mutate scope databases, module folders, provider state, GitHub tokens, or repository cache files. It reports what MMRL can see, how it resolved a decision, and which next action is safe for the user.

Sensitive data policy:

- Authorization headers are redacted.
- GitHub tokens beginning with `ghp_`, `github_pat_`, `gho_`, `ghu_`, `ghs_`, or `ghr_` are redacted.
- Cookies are redacted.
- Root module paths are preserved because they are required to diagnose module identity and provider placement.

## Phase 3 guarded remediation actions

The Debug Workbench may expose guarded remediation buttons after the read-only probes land. These actions are intentionally narrow:

- Open resolved manager uses `LsposedRepository.lsposedManagerIntent()` and never invents package names at click time.
- Run provider action bridge uses `LsposedRepository.providerRefreshPlan()` and only starts `ActionActivity` for the active provider module id selected by that plan.
- Start repository refresh starts the existing `RepositoryService`; the foreground notification remains the source of success/failure counts.
- Stop repository refresh stops the existing `RepositoryService`.

These actions must not expose arbitrary shell input, print tokens, alter scope databases directly, or bypass the provider refresh plan. The copied debug report remains redacted after actions run.

## Phase 4 support bundle export

Phase 4 adds a guarded **Share support bundle** action. It writes a short-lived ZIP into the app cache and shares it through the existing `FileProvider` authority.

Bundle contents:

- `debug-report.txt` with the same redacted support text as **Copy redacted report**;
- `debug-report.json` with grouped probe results, evidence, remedies, and the last guarded action result;
- `README.txt` describing what is intentionally preserved and what is redacted.

The exporter is still support-safe and read-only:

- it does not run shell commands;
- it does not mutate provider modules, repository cache, scope databases, or token storage;
- it redacts Authorization headers, GitHub tokens, and cookies before writing ZIP entries;
- it preserves root module paths, package names, HTTP status codes, and probe labels because those are necessary for diagnosing manager/provider/repo bugs.

Phase 4 also migrates the screen from deprecated `LocalClipboardManager` to `LocalClipboard` with `ClipEntry`, so the debug screen remains warning-clean on current Compose.

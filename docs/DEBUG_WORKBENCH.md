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

# Debug Workbench final seal

This document seals the MMRL Debug Workbench roadmap after the guided diagnostics phase.

## Completed phases

1. **Phase 1/2 probe pack**: introduced the Debug Workbench screen, shared probe/result models, redaction, LSPosed/Vector manager visibility probes, provider module scan, Xposed repository endpoint matrix, GitHub token status, and copied redacted report.
2. **Phase 3 guarded actions**: added narrow remediation actions for opening the resolved manager, running the provider action bridge selected by `LsposedRepository.providerRefreshPlan()`, and starting/stopping the existing repository refresh service.
3. **Phase 4 support bundle**: added sanitized ZIP export/share through `FileProvider` with `debug-report.txt`, `debug-report.json`, and `README.txt`, and removed the deprecated Compose clipboard API.
4. **Phase 5 history and comparisons**: added local bounded history, current-vs-previous comparisons, fixed/regressed/newly failing/improved highlights, clear-history action, and support-bundle history files.
5. **Phase 6 guided diagnostics flows**: added focused flows for manager not recognized, Xposed repo 403, and GitHub token problems, plus `debug-guide.txt`, `debug-guide.json`, and active issue-flow metadata in the support bundle.
6. **Phase 7 final seal**: locks the support, redaction, non-mutation, and no-warning contracts without changing runtime behavior.

## Sealed behavior

The Debug Workbench may diagnose and explain these support issues:

- MMRL cannot recognize an installed LSPosed/libxposed/Vector manager.
- Vector provider module files are present but the manager launch path is unclear.
- The Xposed repository primary endpoint returns HTTP 403 and fallback endpoints must be checked.
- A GitHub token is missing, undecryptable, or not helping GitHub-backed fallbacks.

The Debug Workbench may expose only these guarded actions:

- run read-only probes;
- copy a redacted text report;
- export/share a sanitized support bundle;
- open the resolved manager intent;
- run the provider action bridge selected by the provider refresh plan;
- start or stop the existing repository refresh service;
- clear only the local Debug Workbench history.

## Sealed non-goals

The Debug Workbench must not:

- expose arbitrary shell input;
- run ad-hoc root commands outside the existing narrow provider/repository bridges;
- write LSPosed scope databases;
- mutate provider module folders;
- mutate repository caches while diagnosing;
- mutate GitHub token storage while diagnosing;
- persist raw evidence rows, raw root command output, cookies, Authorization headers, or GitHub tokens in history;
- export raw GitHub tokens, Authorization headers, or cookies in support bundles;
- reintroduce deprecated `LocalClipboardManager` usage.

## Support bundle contract

A complete support bundle includes:

- `debug-report.txt`;
- `debug-report.json`;
- `debug-history.txt`;
- `debug-history.json`;
- `debug-guide.txt`;
- `debug-guide.json`;
- `README.txt`.

The bundle intentionally preserves root module paths, package names, probe ids, probe statuses, HTTP status codes, selected guided-flow metadata, and redacted summaries. These values are necessary for support. Secrets remain redacted.

## Validation contract

The final seal should validate with the same focused unit-test and lint gate as the preceding Debug Workbench overlays:

```bash
devtool \
  --copy \
  --auto-hud \
  --hud-mode desktop-window \
  --yes \
  -r "$HOME/Code/MMRL" \
  --target mmrl_android \
  apply-overlay "$HOME/Downloads/mmrl_debug_workbench_final_seal_overlay.zip" \
  --validate \
  --task :app:testOfficialDebugUnitTest \
  --task :app:lintOfficialDebug
```

A successful seal has zero failing tests, zero skipped tests, zero diagnostics errors, and zero diagnostics warnings.

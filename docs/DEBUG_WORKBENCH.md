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

## Phase 5 history and comparisons

Phase 5 adds a bounded local history for Debug Workbench probe runs. Each run stores only redacted probe ids, titles, groups, statuses, and summaries under the app private files directory. The history is intentionally small, local, and support-oriented: it exists to compare the current run against the previous run and highlight newly failing checks, regressions, improved checks, and checks fixed since the last run.

History safeguards:

- only redacted summaries are persisted;
- evidence rows, GitHub tokens, Authorization headers, cookies, scope database contents, and raw root command output are not persisted in history;
- at most 10 snapshots are retained;
- **Clear local history** deletes only Debug Workbench history and must not touch modules, repository caches, LSPosed scope databases, provider modules, or token storage;
- support bundle export includes `debug-history.txt` and `debug-history.json` so intermittent repo 403s, provider visibility changes, and AshReXcue recognition changes can be compared by support without exposing secrets.

The comparison UI is passive. It does not re-run actions, mutate state, or infer repairs automatically. It only reports status changes between the current probe result and the previous local snapshot.

## Phase 6 guided diagnostics flows

Phase 6 adds issue-specific guided diagnostic flows on top of the existing read-only probes. A guided flow is a focused lens over the same probe runner, not a new privileged executor.

Guided flows:

- **Manager not recognized** focuses on LSPosed/libxposed/Vector manager package visibility and provider fallback evidence.
- **Xposed repo 403** focuses on the repository endpoint matrix and app-wide GitHub token status.
- **AshReXcue not detected** focuses on AshReXcue folder, `module.prop` id/name, canonical alias, and staged-vs-active module evidence.
- **GitHub token problems** focuses on encrypted token availability/decryption and whether GitHub-backed repository fallbacks still fail.

Each flow runs the normal read-only probe set, filters the results to the relevant probe ids, and produces remedy cards. The cards are intentionally human-readable so support can tell whether the failure is package visibility, missing launch intent, primary repository 403, missing token, broken token decryption, or an unknown module identity alias.

Guided diagnostic safeguards:

- flows must not add arbitrary shell input;
- flows must not mutate module folders, LSPosed scope databases, provider modules, repository cache files, or token storage;
- flows must not print raw GitHub tokens, Authorization headers, or cookies;
- support bundles include `debug-guide.txt` and `debug-guide.json` with only the active flow name, focused summaries, and redacted remedy cards;
- the active issue flow is recorded as metadata in `debug-report.json` so exported bundles show which support path the user selected.

## Phase 7 final seal

Phase 7 locks the Debug Workbench contract after the probe pack, guarded actions, support bundle, history, and guided diagnostics phases have landed.

The final seal is documentation and regression-contract only. It does not add new runtime probes, new root commands, new provider actions, repository mutations, scope database writes, or token storage mutations.

Sealed capabilities:

- read-only probes for GitHub token status, LSPosed/libxposed/Vector manager visibility, provider module scan, Xposed repository endpoint matrix, and AshReXcue identity;
- guarded actions limited to resolved manager opening, provider refresh bridge, repository service start/stop, support bundle export, and local history clearing;
- sanitized support bundles with `debug-report.txt`, `debug-report.json`, `debug-history.txt`, `debug-history.json`, `debug-guide.txt`, `debug-guide.json`, and `README.txt`;
- bounded local history of at most 10 redacted summaries;
- guided diagnostics for manager recognition, Xposed repo 403, AshReXcue detection, and GitHub token issues;
- source contracts that keep tokens, Authorization headers, cookies, evidence rows, root command output, module/provider state, repository cache state, and LSPosed scope databases out of unsafe mutation paths.

Release gate:

- unit tests must pass;
- Android lint must report zero errors and zero warnings;
- debug exports must remain redacted;
- `LocalClipboardManager` must not return;
- arbitrary shell execution must not be introduced in the debug package;
- debug history and guided diagnostics must remain local, bounded, redacted, and support-oriented.

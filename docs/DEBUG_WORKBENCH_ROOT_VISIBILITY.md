# Debug Workbench root visibility hardening

This phase improves evidence for cases where provider modules or manager packages are not visible as expected, including:

- `org.matrix.vector.manager` not visible even when a Vector manager APK is installed;
- no provider module found even when `zygisk_vector` or LSPosed is active;
- `modules.lsposed.org/modules.json` returning HTTP 403 while the backup endpoint succeeds.

The goal is to make the failing layer visible without adding unsafe controls.

## Root module evidence

Provider probes inspect both module roots:

- `/data/adb/modules`
- `/data/adb/modules_update`

The support evidence records root existence, directory/readability state, child count, a bounded children preview, listing errors, `module.prop` readability, and the canonical provider match. The probe remains read-only.

This distinguishes cases such as:

- app-process file access is restricted while root access succeeds;
- a module is staged but not active;
- `module.prop` is unreadable or malformed;
- a known provider module exists but expected files are incomplete.

The same root-aware read path is used for runtime provider detection so diagnostics and runtime decisions do not drift.

## Vector manager handling

Manager diagnostics include package visibility and launch-resolution evidence for `org.matrix.vector.manager`, `org.matrix.vector.daemon`, normal launcher intents, package-specific `LAUNCH_MANAGER` actions, and the LSPosed compatibility launch action.

## Repository fallback evidence

The repository probe records the primary `modules.lsposed.org` result, the `backup.modules.lsposed.org` fallback, and the generated jsDelivr main-index fallback without mutating repository state.

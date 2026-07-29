# LSPosed repository and service lifecycle hotfix

This hotfix keeps the Debug Workbench sealed while fixing three runtime rough edges found after Phase 8.

## LSPosed repository JSON tolerance

The LSPosed repository can publish records with `scope = null`, a missing `scope`, or non-string `additionalAuthors` entries. MMRL now keeps the public `scope` accessor non-null and ignores `additionalAuthors` during decoding instead of failing the whole repository index.

## AshReXcue recognition before probes

AshReXcue runtime status now has a root-aware module locator fallback. If the root service reports that the module is missing but the module folder can be identified from `/data/adb/modules`, MMRL can show the installed state without requiring the user to run Debug Workbench probes first.

## Repository refresh service lifecycle

Debug Workbench repository refresh is now a one-shot foreground request. It starts, refreshes repositories once, then calls `stopSelf(startId)` and returns `START_NOT_STICKY`. The user-facing auto-update setting remains the only path for a persistent periodic repository service.

## Warning cleanup

The parked Kotlin diagnostics are fixed:

- `GitHubTokenDebugProbe` no longer uses an unnecessary non-null assertion.
- `LsposedModels` uses an explicit `@param:Json` target for the `scope` field.

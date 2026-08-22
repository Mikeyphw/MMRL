# LSPosed repository and service lifecycle hotfix

This hotfix keeps the Debug Workbench sealed while fixing three runtime rough edges found after Phase 8.

## LSPosed repository JSON tolerance

The LSPosed repository can publish records with `scope = null`, a missing `scope`, or non-string `additionalAuthors` entries. MMRL now keeps the public `scope` accessor non-null and ignores `additionalAuthors` during decoding instead of failing the whole repository index.


## Repository refresh service lifecycle

Debug Workbench repository refresh is now a one-shot foreground request. It starts, refreshes repositories once, then calls `stopSelf(startId)` and returns `START_NOT_STICKY`. The user-facing auto-update setting remains the only path for a persistent periodic repository service.

## Warning cleanup

The parked Kotlin diagnostics are fixed:

- `GitHubTokenDebugProbe` no longer uses an unnecessary non-null assertion.
- `LsposedModels` uses an explicit `@param:Json` target for the `scope` field.

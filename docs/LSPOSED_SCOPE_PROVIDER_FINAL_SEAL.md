# LSPosed scope/provider final seal

This seal closes the staged LSPosed/Vector roadmap on top of the Vector provider baseline and the repository-token/AshReXcue hotfix.

## Completed roadmap

1. **Scope discovery**: read-only root copy of `/data/adb/lspd/modules_config.db`, local installed-target discovery, and source contracts for the DB schema path.
2. **Guarded scope editor**: review-first scope plans, package target sanitizing, SQLite transactions, root-side backups, and WAL/SHM cleanup after restore.
3. **LSPosed manager seal**: explicit manager-open mode selection for installed manager app, active provider action bridge, bundled manager APK, and unavailable providers.
4. **Provider refresh bridge**: post-write refresh planning that opens the installed manager, runs the active provider action bridge, or gives reboot fallback guidance.
5. **Repository/token/AshReXcue hotfix**: LSPosed/Xposed repository mirror fallback, app-wide encrypted GitHub API token reuse, and canonical AshReXcue installed-module aliases.
6. **Final integration/regression seal**: documentation and source contracts that lock the full APK-module, provider, scope, refresh, repository, token, and installed-identity stack together.

## Non-goals kept sealed

- MMRL does not silently install APK modules.
- MMRL does not silently activate APK modules.
- MMRL does not mutate provider scope without an explicit review/apply step.
- MMRL does not treat Vector or LSPosed framework root modules as APK modules.
- MMRL does not pretend a bundled `manager.apk` is launchable unless Android exposes an installed manager package.
- MMRL does not store raw GitHub API tokens in `UserPreferences`.
- MMRL does not use repository metadata alone to decide that AshReXcue is missing.

## Validation expectation

The final seal is intentionally contract/documentation only. It should validate with the unit-test and lint gates used for the earlier LSPosed seal overlays.

# LSPosed final integration seal

This document locks the LSPosed APK module support stack after the repository, installed, governance, safety, adaptive-polish, Vector provider, scope discovery, guarded scope editor, manager seal, provider refresh bridge, and repository-token hotfix overlays.

## Sealed navigation contract

- Repository keeps two ecosystems separate: root ZIP modules and LSPosed APK modules.
- Modules keeps two installed surfaces separate: root ZIP modules and LSPosed/Xposed APK modules.
- The LSPosed tabs remain inside the existing Repository and Modules destinations, so the primary bottom navigation does not gain another slot.

## Sealed install contract

- LSPosed modules install as APKs.
- MMRL performs a review-first APK download/install handoff.
- APK installation is not treated as activation. Users still enable the module and choose scope inside LSPosed/Vector.
- MMRL keeps an Open LSPosed action available wherever activation or scope review is relevant.

## Sealed repository contract

- The LSPosed/Xposed repository path prefers the documented `modules.lsposed.org` JSON endpoints and falls back to the generated `gh-pages` mirror when a provider returns 403 or fails.
- GitHub-backed repository, detail, artifact, nightly, and APK requests share the app-wide GitHub API token stored by `GitHubTokenStore`.
- The raw GitHub token is not stored in `UserPreferences`.

## Sealed provider contract

- Vector and compatible LSPosed framework root modules are providers, not APK modules.
- Recognized framework provider IDs include `zygisk_vector`, `zygisk_lsposed`, `riru_lsposed`, and `lsposed`.
- Manager opening is sealed as installed manager app, active provider action bridge, bundled manager APK detected, or unavailable.
- A bundled `manager.apk` is surfaced as detected-but-not-directly-openable unless Android exposes it as an installed launchable package.

## Sealed scope contract

- Scope discovery reads `/data/adb/lspd/modules_config.db` through a root-copied read-only database file.
- Guarded scope edits are review-first and write only the selected module row plus its scope rows.
- The editor uses a SQLite transaction, creates root-side backups, and removes stale WAL/SHM files after restoring the base DB.
- Provider refresh is explicit after writes: open installed manager, run the active provider action bridge, or fall back to reboot guidance.

## Sealed installed-module identity contract

- Repository author/name metadata does not decide whether a local module is installed.
- Ordinary module IDs use normalized exact identity unless a provider-specific model owns an explicit alias.

## Sealed governance contract

- LSPosed APK modules support the same local governance shape as root modules: follow latest, ignore updates, lock current version, and max versionCode.
- Blocked updates remain visible as policy-blocked, not silently hidden.
- Metadata snapshots include LSPosed APK modules and compare them as current, changed, missing, extra, or repository-match changed.

## Sealed safety contract

- Installed APK modules that do not match modules.lsposed.org are shown as unmatched rather than folded into the repository source.
- Missing LSPosed Manager or provider bridge is actionable.
- Missing source links remain review warnings.
- Review dialogs remain the boundary before APK install/update handoff.

## Regression hooks

`LsposedFinalIntegrationSealTest` asserts that the two tab surfaces, review-first APK boundary, Open LSPosed guidance, governance/snapshot models, and the warning cleanup remain present.

`LsposedProviderRefreshFinalSealContractTest` seals the completed provider/scope/refresh/repository-token roadmap so the Vector provider bridge, guarded DB writes, manager-open modes, refresh bridge, repository fallback, app-wide GitHub token and documentation cannot drift independently.

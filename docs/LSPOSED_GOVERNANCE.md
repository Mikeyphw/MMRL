# LSPosed APK Governance

This pass extends the LSPosed/Xposed APK support with the same review-first governance style used for root modules.

## Version policies

Policies are local to the device and keyed by normalized APK package name.

Supported modes:

- Follow latest
- Ignore updates
- Lock current APK version
- Allow updates only up to the current versionCode

Locked LSPosed APK updates remain visible on the installed-module card as blocked updates. The update button is disabled while the policy blocks the repository candidate.

## Metadata snapshots

LSPosed snapshots are metadata-only. They record installed APK module package names, labels, descriptions, installed version names/codes, repository version data, repository match state, launcher availability, Xposed metadata detection, and local version policy.

Snapshots do not attempt to enable or scope LSPosed modules. MMRL still hands activation and scope review to LSPosed Manager.

## Restore planning

Snapshot comparison produces a review plan only:

- Current
- Version changed
- Missing
- Extra
- Repository match changed

No APK is silently downgraded, reinstalled, uninstalled, enabled, or scoped from a snapshot.

## Warning fixed

This overlay also removes the redundant Elvis fallback from `LsposedRepository.installedModules`; `PackageInfo.packageName` is non-null on the supported API surface.

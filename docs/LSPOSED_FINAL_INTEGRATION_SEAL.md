# LSPosed final integration seal

This document locks the LSPosed APK module support stack after the repository, installed, governance, safety, and adaptive-polish overlays.

## Sealed navigation contract

- Repository keeps two ecosystems separate: root ZIP modules and LSPosed APK modules.
- Modules keeps two installed surfaces separate: root ZIP modules and LSPosed/Xposed APK modules.
- The LSPosed tabs remain inside the existing Repository and Modules destinations, so the primary bottom navigation does not gain another slot.

## Sealed install contract

- LSPosed modules install as APKs.
- MMRL performs a review-first APK download/install handoff.
- APK installation is not treated as activation. Users still enable the module and choose scope inside LSPosed Manager.
- MMRL keeps an Open LSPosed action available wherever activation or scope review is relevant.

## Sealed governance contract

- LSPosed APK modules support the same local governance shape as root modules: follow latest, ignore updates, lock current version, and max versionCode.
- Blocked updates remain visible as policy-blocked, not silently hidden.
- Metadata snapshots include LSPosed APK modules and compare them as current, changed, missing, extra, or repository-match changed.

## Sealed safety contract

- Installed APK modules that do not match modules.lsposed.org are shown as unmatched rather than folded into the repository source.
- Missing LSPosed Manager is actionable.
- Missing source links remain review warnings.
- Review dialogs remain the boundary before APK install/update handoff.

## Regression hooks

`LsposedFinalIntegrationSealTest` asserts that the two tab surfaces, review-first APK boundary, Open LSPosed guidance, governance/snapshot models, and the warning cleanup remain present.

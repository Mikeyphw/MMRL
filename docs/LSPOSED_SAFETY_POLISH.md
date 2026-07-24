# LSPosed safety and APK review polish

This pass keeps LSPosed/Xposed APK support review-first. MMRL can download and hand an APK to Android's installer, but it does not claim that the module is active. LSPosed Manager still owns enablement and app scope.

## Added behavior

- Repository installs now open an APK review dialog before download/installer handoff.
- Installed-tab updates use the same review dialog.
- LSPosed module cards surface safety states:
  - scope review needed
  - not matched to modules.lsposed.org
  - no source link
  - LSPosed Manager missing or hidden
  - update blocked by local policy
- The Installed LSPosed tab keeps Open LSPosed visible because scope and enablement cannot be safely inferred by MMRL yet.

## Non-goals

- No silent APK install.
- No fake enable/scope controls.
- No automatic uninstall or snapshot restore.

# LSPosed APK module support

This overlay adds the first two LSPosed/Xposed APK-module support phases.

## Repository tab

Repository now exposes a third tab:

- Root modules
- GitHub
- LSPosed

The LSPosed tab reads `https://modules.lsposed.org/modules.json` and uses per-module details from `https://modules.lsposed.org/module/<package>.json` only when an APK asset is needed. The implementation follows the LSPosed repository API rather than scraping HTML.

Cards show the package name, title/summary, description, latest version, installation state, source link, website link, and an APK install/update action.

## Modules tab

Modules now exposes:

- Root modules
- LSPosed

The LSPosed tab matches installed APK packages against the LSPosed repository index. It also includes locally installed apps with legacy Xposed metadata such as `xposedmodule`, even when they are not in the public repository.

## Activation boundary

MMRL installs or updates the APK. It does not claim the module is enabled. Users must still open LSPosed Manager to enable the module and choose app scope.

## Safety

APK install goes through Android's package installer. MMRL does not silently enable LSPosed modules and does not attempt scope mutation.

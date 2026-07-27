# LSPosed scope discovery

MMRL reads the Vector/LSPosed provider database at `/data/adb/lspd/modules_config.db` through root by copying it into app cache and opening the copy read-only.

The LSPosed repository tab is allowed to degrade when network/API access fails: provider detection, installed APK-module detection, and scope viewing still load from local device state.

This overlay is read-only. It does not write LSPosed scope data.

Root copy commands single-quote database and cache paths before passing them to the shell.

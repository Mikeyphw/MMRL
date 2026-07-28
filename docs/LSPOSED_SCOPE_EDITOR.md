# LSPosed scope editor

MMRL writes Vector/LSPosed provider scope changes by copying `/data/adb/lspd/modules_config.db` to app cache, editing the copy with SQLite transactions, then restoring it with root after creating a timestamped backup.

The editor is review-first in UI and writes only the `modules.enabled`, optional `modules.auto_include`, and `scope` rows for the selected module package. WAL/SHM files are removed after restore so the provider reopens the base DB cleanly.

After a successful write, the UI exposes **Refresh provider** guidance from the provider refresh bridge: open the installed manager, run the active provider action bridge, or show reboot fallback guidance when no bridge is available.

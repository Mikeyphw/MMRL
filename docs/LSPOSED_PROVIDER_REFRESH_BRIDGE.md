# LSPosed provider refresh bridge

MMRL writes LSPosed scope edits through a guarded copy of `/data/adb/lspd/modules_config.db`. After the write succeeds, the app now computes an explicit provider refresh plan instead of leaving the user with vague reboot guidance.

Refresh precedence:

1. Open an installed manager app when the Android package manager exposes a launch intent.
2. Run the active provider action bridge when Vector or another compatible LSPosed provider exposes `action.sh`.
3. Use a reboot fallback when neither bridge is available.

This keeps scope writes read-review-apply first, preserves root-side DB backups, and makes provider refresh a deliberate UI action. The bundled `manager.apk` state remains visible but is not treated as a direct refresh bridge.

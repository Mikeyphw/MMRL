package com.dergoogler.mmrl.ash.root;

/** Fixed, typed privileged interface. No generic command execution is exposed. */
interface IAshReXcueService {
    String moduleState();
    String serviceInfo();
    String capabilities();
    String snapshot(int activityLimit);
    String setSetting(String key, String value);
    String setSettings(in String[] keys, in String[] values);
    String setTrust(String folder, String trust);
    String restoreOne(String folder);
    String restoreHalf();
    String restoreBatch(in String[] folders);
    String restoreAll();
    String completeTrial();
    String rollbackTrial();
    String discardPendingSettings();
    String exportDiagnostics();
}

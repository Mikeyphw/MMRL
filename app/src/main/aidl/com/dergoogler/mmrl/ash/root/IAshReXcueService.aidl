package com.dergoogler.mmrl.ash.root;

/** Fixed, typed privileged interface. No generic command execution is exposed. */
interface IAshReXcueService {
    boolean moduleAvailable();
    String serviceInfo();
    String status();
    String modules();
    String quarantine();
    String activity(int limit);
    String settings();
    String pendingSettings();
    String setSetting(String key, String value);
    String setSettings(in String[] keys, in String[] values);
    String setTrust(String folder, String trust);
    String restoreOne(String folder);
    String restoreHalf();
    String restoreAll();
    String completeTrial();
    String rollbackTrial();
    String discardPendingSettings();
    String exportDiagnostics();
}

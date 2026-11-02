package com.mikeyphw.mmrl.hooklab.target;

/** Only method the Xposed test module is allowed to intercept. */
public final class ProbeValue {
    private static volatile String baseline = "ORIGINAL_PHASE05";
    private ProbeValue() {}

    // Synchronization and a volatile source prevent a constant from being trivially inlined.
    public static synchronized String readValue() {
        return baseline;
    }
}

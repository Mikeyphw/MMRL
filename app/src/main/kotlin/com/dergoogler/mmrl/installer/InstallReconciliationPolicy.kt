package com.dergoogler.mmrl.installer

/** Pure post-root check: a successful shell result is not success until backend identity/version match. */
object InstallReconciliationPolicy {
    fun matchesExpected(
        actualId: String?,
        actualVersionCode: Int?,
        expectedId: String,
        expectedVersionCode: Int?,
    ): Boolean =
        actualId == expectedId &&
            actualVersionCode != null &&
            (expectedVersionCode == null || actualVersionCode == expectedVersionCode)
}

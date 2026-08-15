package com.dergoogler.mmrl.installer

/** A dependency batch may cross the install boundary only when every planned artifact completed. */
object BulkDownloadCompletionPolicy {
    fun mayInstall(successCount: Int, failureCount: Int, plannedCount: Int): Boolean =
        plannedCount > 0 && successCount == plannedCount && failureCount == 0
}

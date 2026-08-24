package com.dergoogler.mmrl.service

/**
 * Keeps background refreshes fail-closed with respect to previously known-good state.
 * A partial remote generation can add newly observed facts, but it must not erase facts
 * that may simply belong to a source which failed during this attempt.
 */
internal object RefreshBatchPolicy {
    fun shouldRetry(failureCount: Int, runAttemptCount: Int, maxRetries: Int): Boolean =
        failureCount > 0 && runAttemptCount < maxRetries

    fun mergeObservedKeys(
        previous: Set<String>,
        current: Set<String>,
        refreshComplete: Boolean,
    ): Set<String> = if (refreshComplete) current else previous + current
}

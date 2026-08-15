package com.dergoogler.mmrl.operation

import com.dergoogler.mmrl.database.entity.history.OperationHistoryEntity
import com.dergoogler.mmrl.database.entity.history.OperationStatus

/** Safety-aware hard retention: only resolved, removable rows may be evicted. */
object OperationHistoryRetentionPolicy {
    fun evictionsForInsert(
        entries: List<OperationHistoryEntity>,
        maxEntries: Int,
        slotsNeeded: Int = 1,
    ): List<String> {
        require(maxEntries > 0)
        require(slotsNeeded in 1..maxEntries)
        val targetExisting = maxEntries - slotsNeeded
        val needed = (entries.size - targetExisting).coerceAtLeast(0)
        if (needed == 0) return emptyList()
        val candidates = entries
            .asSequence()
            .filter(::isSafelyEvictable)
            .sortedBy(OperationHistoryEntity::startedAt)
            .take(needed)
            .map(OperationHistoryEntity::id)
            .toList()
        require(candidates.size == needed) {
            "Operation history safety limit reached; reconcile unknown/active/pending-reboot operations before starting another operation"
        }
        return candidates
    }

    fun clearable(entries: List<OperationHistoryEntity>): List<String> =
        entries.filter(::isSafelyEvictable).map(OperationHistoryEntity::id)

    fun isSafelyEvictable(entry: OperationHistoryEntity): Boolean =
        entry.canDelete && entry.status in setOf(
            OperationStatus.SUCCEEDED.name,
            OperationStatus.FAILED.name,
            OperationStatus.CANCELLED.name,
        )
}

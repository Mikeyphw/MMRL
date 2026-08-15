package com.dergoogler.mmrl.installer

/** Shared pure bounds used by operation-scoped staging/rollback stores. */
object OperationStoragePolicy {
    fun safeOperationId(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").also {
            require(it.isNotBlank()) { "Operation ID must not be blank" }
        }

    fun canFit(existingBytes: Long, incomingBytes: Long, maxBytes: Long): Boolean =
        existingBytes >= 0 && incomingBytes >= 0 && maxBytes >= 0 &&
            runCatching { Math.addExact(existingBytes, incomingBytes) <= maxBytes }.getOrDefault(false)

    fun isExpired(lastModified: Long, now: Long, maxAgeMs: Long): Boolean =
        maxAgeMs >= 0 && now >= lastModified && now - lastModified > maxAgeMs

    fun canPruneOperation(operationId: String, leasedOperationIds: Set<String>): Boolean =
        operationId !in leasedOperationIds
    fun addWithinLimit(currentBytes: Long, emittedBytes: Int, maxBytes: Long): Long {
        require(currentBytes >= 0L && emittedBytes >= 0 && maxBytes >= 0L) { "Byte counts must be non-negative" }
        val next = runCatching { Math.addExact(currentBytes, emittedBytes.toLong()) }
            .getOrElse { throw IllegalArgumentException("Byte count overflow", it) }
        require(next <= maxBytes) { "Operation storage byte limit exceeded" }
        return next
    }
}

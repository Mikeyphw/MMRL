package com.dergoogler.mmrl.service

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local ownership gate for logical download operation IDs.
 *
 * A logical operation may have at most one active attempt. Every successful claim receives an
 * opaque attempt ID so later download state can be scoped to that exact execution rather than to
 * a reusable history/operation ID. Release is compare-and-remove: a stale attempt can never
 * release a newer owner.
 */
internal class DownloadOperationOwnership(
    private val newAttemptId: () -> String = { UUID.randomUUID().toString() },
) {
    private val owners = ConcurrentHashMap<String, String>()

    fun claim(operationId: String): ClaimResult {
        require(operationId.isNotBlank()) { "Download operation ID must not be blank" }
        val attemptId = newAttemptId()
        require(attemptId.isNotBlank()) { "Download attempt ID must not be blank" }
        val existing = owners.putIfAbsent(operationId, attemptId)
        return if (existing == null) {
            ClaimResult.Claimed(Lease(operationId, attemptId))
        } else {
            ClaimResult.AlreadyOwned(operationId, existing)
        }
    }

    fun currentAttempt(operationId: String): String? = owners[operationId]

    fun release(lease: Lease): Boolean = owners.remove(lease.operationId, lease.attemptId)

    fun clear() = owners.clear()

    data class Lease(val operationId: String, val attemptId: String)

    sealed interface ClaimResult {
        data class Claimed(val lease: Lease) : ClaimResult
        data class AlreadyOwned(val operationId: String, val activeAttemptId: String) : ClaimResult
    }
}

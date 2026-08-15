package com.dergoogler.mmrl.ash.data

/** Chooses whether an Ash mutation owns a new history row or an externally-created durable row. */
object AshMutationOwnershipPolicy {
    data class Ownership(
        val existingHistoryId: String?,
        val idempotencyKey: String,
        val createHistory: Boolean,
    )

    fun resolve(
        existingHistoryId: String?,
        externalIdempotencyKey: String?,
        generatedIdempotencyKey: String,
    ): Ownership {
        val existing = existingHistoryId?.takeIf(String::isNotBlank)
        val external = externalIdempotencyKey?.takeIf(String::isNotBlank)
        require((existing == null) == (external == null)) {
            "External Ash history ownership requires both history ID and idempotency key"
        }
        return if (existing == null) {
            Ownership(null, generatedIdempotencyKey, createHistory = true)
        } else {
            Ownership(existing, requireNotNull(external), createHistory = false)
        }
    }
}

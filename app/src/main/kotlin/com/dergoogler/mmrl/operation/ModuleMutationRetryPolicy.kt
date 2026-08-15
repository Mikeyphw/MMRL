package com.dergoogler.mmrl.operation

/** State-marker mutations are idempotent, but only clearly transient callback failures are offered as retries. */
object ModuleMutationRetryPolicy {
    private val transientTokens = listOf(
        "temporarily unavailable",
        "try again",
        "busy",
        "timeout",
        "timed out",
        "service unavailable",
        "connection reset",
    )

    fun isRetryable(message: String?): Boolean {
        val normalized = message?.trim()?.lowercase().orEmpty()
        return normalized.isNotEmpty() && transientTokens.any(normalized::contains)
    }
}

package com.dergoogler.mmrl.repository

import android.content.Context
import java.security.MessageDigest

/** Durable provenance for repositories explicitly approved from external deep links. */
class RepositoryDeepLinkProvenanceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("repository_deeplink_provenance_v1", Context.MODE_PRIVATE)

    data class Record(
        val normalizedUrl: String,
        val source: String,
        val userApproved: Boolean,
        val approvedAtEpochMs: Long,
    )

    fun recordApproved(normalizedUrl: String, source: String = "deep-link", approvedAtEpochMs: Long = System.currentTimeMillis()) {
        val key = keyFor(normalizedUrl)
        prefs.edit()
            .putString("$key.url", normalizedUrl)
            .putString("$key.source", source)
            .putBoolean("$key.approved", true)
            .putLong("$key.approvedAt", approvedAtEpochMs)
            .commit().also { committed ->
                check(committed) { "Failed to durably record repository deep-link approval provenance" }
            }
    }

    fun load(normalizedUrl: String): Record? {
        val key = keyFor(normalizedUrl)
        val url = prefs.getString("$key.url", null) ?: return null
        if (url != normalizedUrl || !prefs.getBoolean("$key.approved", false)) return null
        return Record(url, prefs.getString("$key.source", "deep-link") ?: "deep-link", true, prefs.getLong("$key.approvedAt", 0L))
    }

    private fun keyFor(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

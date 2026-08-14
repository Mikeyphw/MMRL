package com.dergoogler.mmrl.ui.activity.terminal

import android.net.Uri
import com.dergoogler.mmrl.platform.model.ModId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps trusted privileged-launch semantics out of Android Intent extras.
 *
 * The token is deliberately process-local: if the process dies before the internal Activity consumes
 * the request, the launch fails closed instead of reconstructing privileged modes from caller data.
 * Entries are bounded and short-lived so abandoned launches do not become an unbounded process cache.
 */
internal object PrivilegedLaunchSessions {
    data class InstallRequest(
        val uris: List<Uri>,
        val confirm: Boolean,
        val parentOperationId: String?,
        val rollbackMode: Boolean,
        val expectedModuleIds: List<String>,
    )

    data class ActionRequest(
        val moduleId: ModId,
    )

    private val installs = SessionStore<InstallRequest>()
    private val actions = SessionStore<ActionRequest>()

    fun createInstall(request: InstallRequest): String {
        require(request.uris.isNotEmpty()) { "Install session requires at least one URI" }
        require(
            request.expectedModuleIds.isEmpty() ||
                request.expectedModuleIds.size == request.uris.size,
        ) { "Expected module IDs must be empty or match the URI count" }
        request.expectedModuleIds.forEach { value ->
            require(ModId.parseOrNull(value) != null) { "Invalid expected module ID: $value" }
        }
        return installs.put(
            request.copy(
                uris = request.uris.toList(),
                expectedModuleIds = request.expectedModuleIds.toList(),
            ),
        )
    }

    fun getInstall(token: String?): InstallRequest? = installs.get(token)

    fun createAction(moduleId: ModId): String =
        actions.put(ActionRequest(moduleId.requireOperational()))

    fun getAction(token: String?): ActionRequest? = actions.get(token)

    private class SessionStore<T> {
        private data class Entry<T>(
            val createdAt: Long,
            val value: T,
        )

        private val entries = ConcurrentHashMap<String, Entry<T>>()

        fun put(value: T): String {
            prune()
            while (entries.size >= MAX_ENTRIES) {
                val oldest = entries.minByOrNull { it.value.createdAt }?.key ?: break
                entries.remove(oldest)
            }
            val token = UUID.randomUUID().toString()
            entries[token] = Entry(System.currentTimeMillis(), value)
            return token
        }

        fun get(token: String?): T? {
            if (token.isNullOrBlank()) return null
            val entry = entries[token] ?: return null
            if (System.currentTimeMillis() - entry.createdAt > VALIDITY_MS) {
                entries.remove(token, entry)
                return null
            }
            return entry.value
        }

        private fun prune() {
            val cutoff = System.currentTimeMillis() - VALIDITY_MS
            entries.entries
                .filter { it.value.createdAt < cutoff }
                .forEach { (key, entry) ->
                    entries.remove(key, entry)
                }
        }
    }

    private const val MAX_ENTRIES = 64
    private const val VALIDITY_MS = 2L * 60L * 60L * 1000L
}

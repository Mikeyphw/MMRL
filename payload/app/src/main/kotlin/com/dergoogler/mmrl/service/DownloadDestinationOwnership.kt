package com.dergoogler.mmrl.service

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local reservation table for final download destinations.
 *
 * Selection and reservation are one atomic operation, so two attempts that resolve the same
 * filename cannot both observe a free path and publish to it. A lease is released with
 * compare-and-remove semantics, preventing stale cleanup from releasing a newer reservation.
 */
internal class DownloadDestinationOwnership {
    private val owners = ConcurrentHashMap<String, String>()
    private val selectionLock = Any()

    fun claim(requested: File, attemptId: String, allowExisting: Boolean = false): Lease {
        require(attemptId.isNotBlank()) { "Download attempt ID must not be blank" }
        return synchronized(selectionLock) {
            var index = 0
            while (true) {
                val candidate = candidate(requested, index)
                val key = candidate.canonicalFile.absolutePath
                val unavailableOnDisk = candidate.exists() && !(allowExisting && index == 0)
                if (!unavailableOnDisk && owners.putIfAbsent(key, attemptId) == null) {
                    return@synchronized Lease(candidate, key, attemptId)
                }
                index += 1
            }
            error("unreachable")
        }
    }

    fun release(lease: Lease): Boolean = owners.remove(lease.canonicalPath, lease.attemptId)

    fun currentAttempt(destination: File): String? = owners[destination.canonicalFile.absolutePath]

    fun clear() = owners.clear()

    private fun candidate(destination: File, index: Int): File {
        if (index == 0) return destination
        val parent = destination.parentFile ?: return File("${destination.path} ($index)")
        val name = destination.name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        return parent.resolve("$stem ($index)$ext")
    }

    data class Lease(
        val destination: File,
        val canonicalPath: String,
        val attemptId: String,
    )
}

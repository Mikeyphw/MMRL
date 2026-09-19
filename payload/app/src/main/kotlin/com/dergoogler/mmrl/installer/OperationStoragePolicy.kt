package com.dergoogler.mmrl.installer

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/** Shared pure bounds and containment rules used by operation-scoped stores. */
object OperationStoragePolicy {
    /**
     * Maps a logical operation ID to an opaque, collision-resistant filesystem component.
     *
     * Logical IDs are deliberately never used as path components. This avoids traversal,
     * replacement-sanitizer collisions, and accidental coupling between external IDs and the
     * on-disk layout.
     */
    fun safeOperationId(value: String): String {
        require(value.isNotBlank()) { "Operation ID must not be blank" }
        require(value != "." && value != "..") { "Operation ID must not be a path meta-component" }
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return "op-" + digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Resolves an operation directory and fails closed unless its canonical direct parent is the
     * canonical store root. Existing symlink children therefore cannot redirect operations out of
     * the store.
     */
    @Throws(IOException::class)
    fun operationDirectory(root: File, operationId: String): File {
        val canonicalRoot = root.canonicalFile
        val lexicalChild = File(canonicalRoot, safeOperationId(operationId)).absoluteFile
        require(lexicalChild.parentFile == canonicalRoot) { "Operation directory escapes its store root" }
        val canonicalChild = lexicalChild.canonicalFile
        require(canonicalChild == lexicalChild) { "Operation directory must not redirect through a symbolic link" }
        return canonicalChild
    }

    /** Verifies an already-enumerated child before destructive or mutating operations. */
    @Throws(IOException::class)
    fun requireDirectChild(root: File, child: File): File {
        val canonicalRoot = root.canonicalFile
        val lexicalChild = child.absoluteFile.toPath().normalize().toFile()
        require(lexicalChild.parentFile?.canonicalFile == canonicalRoot) { "Operation directory escapes its store root" }
        val canonicalChild = lexicalChild.canonicalFile
        require(canonicalChild == lexicalChild) { "Operation directory must not redirect through a symbolic link" }
        return canonicalChild
    }

    /**
     * Verifies a regular file directly inside a verified operation directory without losing the
     * caller's lexical path to premature canonicalization. Symlinked operation directories and
     * symlinked files are rejected.
     */
    @Throws(IOException::class)
    fun requireManagedFile(root: File, file: File): File {
        val lexicalFile = file.absoluteFile.toPath().normalize().toFile()
        val lexicalParent = lexicalFile.parentFile ?: throw IllegalArgumentException("Managed file has no parent")
        val managedParent = requireDirectChild(root, lexicalParent)
        val canonicalFile = lexicalFile.canonicalFile
        require(canonicalFile == lexicalFile) { "Managed file must not redirect through a symbolic link" }
        require(canonicalFile.parentFile == managedParent) { "Managed file escapes its operation directory" }
        require(canonicalFile.isFile) { "Managed path is not a regular file" }
        return canonicalFile
    }

    /**
     * Deletes a verified direct child without following nested symbolic links.
     *
     * Absence is not an error and returns false. Any containment/canonicalization/deletion failure
     * is surfaced to the caller instead of being silently ignored.
     */
    @Throws(IOException::class)
    fun deleteDirectChild(root: File, child: File): Boolean {
        val managed = requireDirectChild(root, child)
        val path = managed.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return false

        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                if (error != null) throw error
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
        return true
    }

    @Throws(IOException::class)
    fun deleteOperationDirectory(root: File, operationId: String): Boolean =
        deleteDirectChild(root, operationDirectory(root, operationId))

    /** Returns the bytes reachable inside a verified operation directory without following links. */
    @Throws(IOException::class)
    fun directChildSize(root: File, child: File): Long {
        val managed = requireDirectChild(root, child)
        val path = managed.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return 0L
        var total = 0L
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) total = Math.addExact(total, attrs.size())
                return FileVisitResult.CONTINUE
            }
        })
        return total
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

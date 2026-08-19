package com.dergoogler.mmrl.platform.file

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Central path policy for operations executed by the privileged file-manager service.
 *
 * Paths must be absolute, remain beneath an explicitly approved root, and may not traverse
 * symbolic-link components. Mutation roots are intentionally narrower than read roots.
 */
object PrivilegedPathPolicy {
    enum class Access { READ, MUTATE }

    data class Resolution(
        val root: Path,
        val path: Path,
    ) {
        val relative: Path get() = root.relativize(path)
    }

    private val readRoots = listOf(
        "/data/adb",
        "/data/user",
        "/data/data",
        "/data/local/tmp",
        "/storage",
        "/sdcard",
        "/system",
        "/system_ext",
        "/vendor",
        "/product",
        "/odm",
        "/apex",
        "/metadata",
        "/cache",
        "/proc",
        "/sys",
        "/dev",
    ).map(Paths::get)

    private val mutationRoots = listOf(
        "/data/adb",
        "/data/user",
        "/data/data",
        "/data/local/tmp",
        "/storage",
        "/sdcard",
        "/metadata",
        "/cache",
    ).map(Paths::get)

    fun resolve(path: String, access: Access): Path = resolveWithRoot(path, access).path

    fun resolveWithRoot(path: String, access: Access): Resolution =
        resolveForRootsWithRoot(
            path,
            if (access == Access.MUTATE) mutationRoots else readRoots,
            allowFinalSymlink = false,
        )

    /** Resolve a path for metadata inspection without traversing a final symlink. */
    fun resolveForInspection(path: String, access: Access): Path =
        resolveForInspectionWithRoot(path, access).path

    internal fun resolveForInspectionWithRoot(path: String, access: Access): Resolution =
        resolveForRootsWithRoot(
            path,
            if (access == Access.MUTATE) mutationRoots else readRoots,
            allowFinalSymlink = true,
        )

    internal fun resolveForRoots(
        path: String,
        approvedRoots: List<Path>,
        allowFinalSymlink: Boolean = false,
    ): Path = resolveForRootsWithRoot(path, approvedRoots, allowFinalSymlink).path

    internal fun resolveForRootsWithRoot(
        path: String,
        approvedRoots: List<Path>,
        allowFinalSymlink: Boolean = false,
    ): Resolution {
        require(path.isNotBlank()) { "Path must not be blank" }
        val candidate = Paths.get(path)
        require(candidate.isAbsolute) { "Privileged path must be absolute: $path" }
        if (candidate.any { it.toString() == ".." }) {
            throw SecurityException("Parent traversal is not allowed: $path")
        }

        val normalized = candidate.normalize()
        require(!isPseudoFdPath(normalized)) { "Descriptor pseudo-paths are not privileged file paths: $path" }

        val root = approvedRoots
            .map { it.toAbsolutePath().normalize() }
            .filter { normalized == it || normalized.startsWith(it) }
            .maxByOrNull { it.nameCount }
            ?: throw SecurityException("Path is outside approved privileged roots: $path")

        assertNoSymlinkComponents(root, normalized, allowFinalSymlink)
        return Resolution(root = root, path = normalized)
    }

    private fun isPseudoFdPath(path: Path): Boolean {
        val value = path.toString()
        if (value == "/dev/fd" || value.startsWith("/dev/fd/")) return true
        if (!value.startsWith("/proc/")) return false
        val parts = path.map(Path::toString)
        return parts.size >= 3 && parts[2] == "fd"
    }

    private fun assertNoSymlinkComponents(root: Path, path: Path, allowFinalSymlink: Boolean) {
        var current = root
        if (Files.isSymbolicLink(current)) {
            throw SecurityException("Approved root resolves through a symbolic link: $root")
        }
        val relative = root.relativize(path)
        val components = relative.toList()
        components.forEachIndexed { index, component ->
            current = current.resolve(component)
            val isFinal = index == components.lastIndex
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current) && !(allowFinalSymlink && isFinal)) {
                throw SecurityException("Symbolic-link traversal is not allowed: $current")
            }
        }
    }

    /** Deletes a tree without following directory symbolic links. */
    @Throws(IOException::class)
    internal fun deleteTreeNoFollow(path: Path): Boolean {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return false
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return Files.deleteIfExists(path)
        }
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
        return true
    }
}

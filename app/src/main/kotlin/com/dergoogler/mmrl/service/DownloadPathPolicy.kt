package com.dergoogler.mmrl.service

import java.io.File

/**
 * Normalizes the user-configured download directory before it reaches File or MediaStore APIs.
 *
 * The settings UI accepts both a path relative to the public Downloads directory and an absolute
 * shared-storage path. Android exposes the same shared storage through aliases such as /sdcard and
 * /storage/self/primary, so those aliases are mapped to the root that owns [publicDownloads].
 */
object DownloadPathPolicy {
    private val SHARED_STORAGE_ALIASES = listOf("/sdcard", "/storage/self/primary", "/mnt/sdcard")

    fun resolveDirectory(
        configuredPath: String,
        publicDownloads: File,
    ): File {
        val downloads = canonicalOrNormalized(publicDownloads)
        val raw = configuredPath.trim().ifEmpty { downloads.path }
        val repairedRaw = repairLegacyEscapedTraversal(raw)
        val input = File(repairedRaw)
        val wasRelative = !input.isAbsolute
        val initial = if (wasRelative) downloads.resolve(input.path) else input
        val lexical = initial.absoluteFile.toPath().normalize().toFile()
        val aliased = remapSharedStorageAlias(lexical, downloads.parentFile)
        val resolved = canonicalOrNormalized(aliased)

        if (wasRelative) {
            require(resolved.isInside(downloads)) {
                "Relative download paths must stay inside ${downloads.path}"
            }
        }

        return resolved
    }

    fun displayValue(
        configuredPath: String,
        publicDownloads: File,
    ): String {
        val downloads = canonicalOrNormalized(publicDownloads)
        val resolved = resolveDirectory(configuredPath, downloads)
        return if (resolved.isInside(downloads)) {
            resolved.relativeTo(downloads).path.takeUnless { it == "." }.orEmpty()
        } else {
            resolved.path
        }
    }

    fun destination(
        configuredPath: String,
        filename: String,
        publicDownloads: File,
    ): File {
        val directory = resolveDirectory(configuredPath, publicDownloads)
        val safeFilename = DownloadTargetPolicy.sanitizeFilename(filename)
        val destination = canonicalOrNormalized(directory.resolve(safeFilename))
        require(destination.parentFile == canonicalOrNormalized(directory)) {
            "Invalid download filename"
        }
        return destination
    }

    /**
     * Android's MediaStore rewrites traversal segments such as `..` to `_..` instead of
     * traversing them. Older MMRL builds could persist an absolute path as a relative path from
     * Downloads, producing values such as
     * `/storage/emulated/0/Download/_../_../_../_../sdcard/Download/Modules`.
     *
     * Only decode those escaped segments when the repaired path normalizes to a known shared
     * storage alias. A legitimate directory literally named `_..` is otherwise left untouched.
     */
    private fun repairLegacyEscapedTraversal(rawPath: String): String {
        val normalizedSeparators = rawPath.replace('\\', '/')
        if (normalizedSeparators.split('/').none { it == "_.." }) return rawPath

        val candidate =
            normalizedSeparators
                .split('/')
                .joinToString("/") { segment -> if (segment == "_..") ".." else segment }
        val normalizedCandidate =
            runCatching { File(candidate).absoluteFile.toPath().normalize().toString().replace('\\', '/') }
                .getOrDefault(candidate)

        return if (SHARED_STORAGE_ALIASES.any { alias ->
                normalizedCandidate == alias || normalizedCandidate.startsWith("$alias/")
            }
        ) {
            candidate
        } else {
            rawPath
        }
    }

    private fun remapSharedStorageAlias(
        file: File,
        sharedStorageRoot: File?,
    ): File {
        val root = sharedStorageRoot ?: return file
        val normalizedPath = file.path.replace('\\', '/')
        val alias = SHARED_STORAGE_ALIASES.firstOrNull {
            normalizedPath == it || normalizedPath.startsWith("$it/")
        }
            ?: return file
        val suffix = normalizedPath.removePrefix(alias).trimStart('/')
        return if (suffix.isEmpty()) root else root.resolve(suffix)
    }

    private fun canonicalOrNormalized(file: File): File =
        runCatching { file.canonicalFile }
            .getOrElse { file.absoluteFile.toPath().normalize().toFile() }

    private fun File.isInside(parent: File): Boolean =
        toPath().startsWith(parent.toPath())
}

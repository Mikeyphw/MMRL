package com.dergoogler.mmrl.service

import java.io.File

enum class ExistingDownload {
    MISSING,
    EMPTY,
    VALID,
}

object DownloadTargetPolicy {
    fun classify(file: File): ExistingDownload =
        when {
            !file.exists() -> ExistingDownload.MISSING
            file.length() <= 0L -> ExistingDownload.EMPTY
            else -> ExistingDownload.VALID
        }

    /**
     * Produces a single safe path segment that Android's MediaStore will not silently rename.
     * In particular, names beginning with a dot are rewritten because MediaStore prefixes those
     * hidden names with an underscore, which previously made MMRL look for a different file path.
     */
    fun sanitizeFilename(filename: String): String {
        val leaf =
            filename
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .trim()
                .replace(Regex("[\\u0000-\\u001f\\u007f]"), "_")
                .replace(Regex("[\\/:*?\"<>|]"), "_")

        require(leaf.isNotBlank() && leaf != "." && leaf != "..") {
            "Invalid download filename"
        }

        return if (leaf.startsWith('.')) "_$leaf" else leaf
    }
}

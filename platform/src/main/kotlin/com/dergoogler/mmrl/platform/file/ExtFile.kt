package com.dergoogler.mmrl.platform.file

import android.os.RemoteException
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Represents an extended file object with additional functionalities.
 * This class inherits from the standard [File] class and provides enhanced path resolution
 * and compatibility with various input types for path construction.
 * It also includes methods for asynchronous and synchronous calculation of file or directory size,
 * with options for recursive calculation, skipping specific paths, and handling symbolic links.
 * Additionally, it provides utility methods to check the type of a file system entry (e.g., block device, character device, symbolic link).
 *
 * @param paths Vararg parameter representing the path components.
 *              These components can be of type [ExtFile], [SuFile], [File], [String], or [Uri].
 *              The constructor intelligently resolves these components into a unified file path.
 * @throws IllegalArgumentException If an unsupported type is provided in the `paths` parameter.
 */
open class ExtFile(
    vararg paths: Any,
) : File(Path.parse(*paths)) {
    open suspend fun lengthAsync(): Long =
        withContext<Long>(Dispatchers.IO) {
            this@ExtFile.length(recursive = false)
        }

    open suspend fun lengthAsync(
        recursive: Boolean = false,
        skipPaths: List<String> = emptyList(),
        skipSymLinks: Boolean = true,
    ): Long =
        withContext<Long>(Dispatchers.IO) {
            this@ExtFile.length(
                recursive = recursive,
                skipPaths = skipPaths,
                skipSymLinks = skipSymLinks,
            )
        }

    open fun length(
        recursive: Boolean = false,
        skipPaths: List<String> = emptyList(),
        skipSymLinks: Boolean = true,
    ): Long = calculateSizeInContext(recursive, skipPaths, skipSymLinks)

    private fun calculateSizeInContext(
        recursive: Boolean,
        skipPaths: List<String>,
        skipSymLinks: Boolean,
    ): Long {
        if (recursive) {
            if (!this.isDirectory()) {
                if (skipSymLinks && this.isSymlink()) return 0L
                return this.length()
            }
            return doRecursiveScan(this, skipPaths, skipSymLinks)
        } else {
            if (skipSymLinks && this.isSymlink()) return 0L
            return this.length()
        }
    }

    private fun doRecursiveScan(
        currentDirSuFile: ExtFile,
        skipPaths: List<String>,
        skipSymLinks: Boolean,
    ): Long {
        val items = currentDirSuFile.list()

        if (items == null) return 0L

        var totalSize = 0L
        for (itemName in items) {
            val itemFullPath = "${currentDirSuFile.path}/$itemName"
            val itemSuFile = SuFile(itemFullPath)

            if (skipPaths.contains(itemFullPath)) {
                continue
            }

            if (skipSymLinks && itemSuFile.isSymlink()) {
                continue
            }

            totalSize +=
                if (itemSuFile.isDirectory()) {
                    itemSuFile.length(
                        recursive = true,
                        skipPaths = skipPaths,
                        skipSymLinks = skipSymLinks,
                    )
                } else {
                    itemSuFile.length()
                }
        }

        return totalSize
    }

    open fun isBlock(): Boolean =
        try {
            OsConstants.S_ISBLK(getMode(path))
        } catch (e: RemoteException) {
            false
        }

    open fun isCharacter(): Boolean =
        try {
            OsConstants.S_ISCHR(getMode(path))
        } catch (e: RemoteException) {
            false
        }

    open fun isSymlink(): Boolean =
        try {
            OsConstants.S_ISLNK(getMode(path))
        } catch (e: RemoteException) {
            false
        }

    open fun isNamedPipe(): Boolean =
        try {
            OsConstants.S_ISFIFO(getMode(path))
        } catch (e: RemoteException) {
            false
        }

    open fun isSocket(): Boolean =
        try {
            OsConstants.S_ISSOCK(getMode(path))
        } catch (e: RemoteException) {
            false
        }

    open fun getMode(path: String?): Int =
        try {
            Os.lstat(path).st_mode
        } catch (e: ErrnoException) {
            0
        }

}

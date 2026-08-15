@file:Suppress("unused", "FunctionName")

package com.dergoogler.mmrl.platform.file

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.O_APPEND
import android.system.OsConstants.O_CREAT
import android.system.OsConstants.O_RDONLY
import android.system.OsConstants.O_TRUNC
import android.system.OsConstants.O_WRONLY
import com.dergoogler.mmrl.platform.Platform
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.stub.IFileManager
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A [File] abstraction that utilizes root privileges for file operations if available,
 * otherwise using standard [java.io.File] operations for an explicitly non-root mode.
 *
 * This class extends [ExtFile] (which itself extends [java.io.File]) and overrides
 * its methods. If a root-enabled [IFileManager] is available through [Platform.fileManagerOrNull],
 * read-only queries may fall back locally, but privileged mutations fail closed when root mode
 * is selected and the root file manager is unavailable or fails.
 *
 * This allows for seamless interaction with the file system, attempting privileged
 * operations when possible and gracefully degrading to non-privileged operations otherwise.
 *
 * It provides constructors to create `SuFile` objects from various path representations
 * (String, File, Uri) and offers methods for common file operations such as
 * reading, writing, listing directories, checking existence, and managing permissions.
 *
 * @param paths Vararg parameter representing the path components. Can be of type [String], [File], or [Uri].
 *              These components will be resolved into a single absolute path.
 *
 * @see java.io.File
 * @see ExtFile
 * @see com.dergoogler.mmrl.platform.stub.IFileManager
 */
class SuFile(
    vararg paths: Any,
) : ExtFile(*paths) {
    fun fromPaths(vararg paths: Any): SuFile? {
        val files = paths.map { SuFile(path, it) }
        for (f in files) {
            if (f.exists()) {
                return f
            }
        }

        return null
    }

    override fun list(): Array<String>? =
        queryFallback(
            { this.list(path) },
            { super.list() },
        )

    override fun length(): Long =
        queryFallback(
            { this.length(path) },
            { super.length() },
        )

    fun stat(): Long = this.lastModified()

    override fun lastModified(): Long =
        queryFallback(
            { this.stat(path) },
            { super.lastModified() },
        )

    override fun exists(): Boolean =
        queryFallback(
            { this.exists(path) },
            { super.exists() },
        )

    @OptIn(ExperimentalContracts::class)
    inline fun <R> exists(block: (SuFile) -> R): R? {
        contract {
            callsInPlace(block, InvocationKind.AT_MOST_ONCE)
        }

        return if (exists()) block(this) else null
    }

    override fun isDirectory(): Boolean =
        queryFallback(
            { this.isDirectory(path) },
            { super.isDirectory() },
        )

    override fun isFile(): Boolean =
        queryFallback(
            { this.isFile(path) },
            { super.isFile() },
        )

    override fun isBlock(): Boolean =
        queryFallback(
            { this.isBlock(path) },
            { super.isBlock() },
        )

    override fun isCharacter(): Boolean =
        queryFallback(
            { this.isCharacter(path) },
            { super.isCharacter() },
        )

    override fun isSymlink(): Boolean =
        queryFallback(
            { this.isSymlink(path) },
            { super.isSymlink() },
        )

    override fun isNamedPipe(): Boolean =
        queryFallback(
            { this.isNamedPipe(path) },
            { super.isNamedPipe() },
        )

    override fun isSocket(): Boolean =
        queryFallback(
            { this.isSocket(path) },
            { super.isSocket() },
        )

    override fun mkdir(): Boolean =
        mutationBoolean(
            { this.mkdir(path) },
            { super.mkdir() },
        )

    override fun mkdirs(): Boolean =
        mutationBoolean(
            { this.mkdirs(path) },
            { super.mkdirs() },
        )

    override fun createNewFile(): Boolean =
        mutationBoolean(
            { this.createNewFile(path) },
            { super.createNewFile() },
        )

    override fun renameTo(dest: File): Boolean =
        mutationBoolean(
            { this.renameTo(path, dest.path) },
            { super.renameTo(dest) },
        )

    fun copyTo(
        dest: File,
        overwrite: Boolean = false,
    ) = mutationUnit(
        { this.copyTo(path, dest.path, overwrite) },
        { File(path).copyTo(dest, overwrite) },
    )

    override fun canExecute(): Boolean =
        queryFallback(
            { this.canExecute(path) },
            { super.canExecute() },
        )

    override fun canRead(): Boolean =
        queryFallback(
            { this.canRead(path) },
            { super.canRead() },
        )

    override fun canWrite(): Boolean =
        queryFallback(
            { this.canWrite(path) },
            { super.canWrite() },
        )

    override fun delete(): Boolean =
        mutationBoolean(
            { this.delete(path) },
            { super.delete() },
        )

    override fun deleteOnExit() {
        mutationUnit(
            {
                this.deleteOnExit(path)
            },
            { super.deleteOnExit() },
        )
    }

    override fun isHidden(): Boolean =
        queryFallback(
            { this.isHidden(path) },
            { super.isHidden() },
        )

    override fun setReadOnly(): Boolean =
        setPermissions(
            SuFilePermissions.combine(
                SuFilePermissions.OWNER_READ,
                SuFilePermissions.GROUP_READ,
                SuFilePermissions.OTHERS_READ,
            ),
        )

    override fun setExecutable(executable: Boolean): Boolean =
        setPermissions(SuFilePermissions.PERMISSION_755)

    fun setPermissions(permissions: SuFilePermissions): Boolean =
        this.setPermissions(permissions.value)

    fun setPermissions(permissions: Int): Boolean =
        mutationBoolean(
            { this.setPermissions(path, permissions) },
            { false },
        )

    val parentSuFile: SuFile?
        get() =
            try {
                val parent = this.parentFile
                if (parent != null && parent.path != this.path) {
                    SuFile(parent)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }

    fun setOwner(
        uid: Int,
        gid: Int,
    ): Boolean =
        mutationBoolean(
            {
                this.setOwner(path, uid, gid)
            },
            {
                try {
                    Os.chown(path, uid, gid)
                    true
                } catch (e: ErrnoException) {
                    false
                }
            },
        )

    override fun listFiles(): Array<SuFile>? = this.list()?.map { SuFile(it, this) }?.toTypedArray()

    override fun listFiles(filter: FileFilter?): Array<SuFile>? {
        val ss = list()
        val files = ArrayList<SuFile>()
        if (ss == null) return null
        for (s in ss) {
            val f = SuFile(s, this)
            if ((filter == null) || filter.accept(f)) files.add(f)
        }
        return files.toArray(arrayOfNulls<SuFile>(files.size))
    }

    // I/O helpers
    @Throws(IOException::class, RemoteException::class)
    internal fun __open_file__(flags: Int, mode: Int): ParcelFileDescriptor {
        val selectedRoot = PlatformManager.preferredPlatform.isPrivilegedRoot
        val manager = PlatformManager.fileManagerOrNull
        return when (PrivilegeRouting.select(selectedRoot, PlatformManager.isAlive && manager != null)) {
            PrivilegeRouting.Backend.ROOT -> try {
                requireNotNull(manager).openFile(path, flags, mode)
            } catch (error: Exception) {
                throw IOException("Privileged file open failed without local fallback: $path", error)
            }
            PrivilegeRouting.Backend.UNAVAILABLE ->
                throw IOException("Privileged file service is unavailable for selected root mode: $path")
            PrivilegeRouting.Backend.LOCAL -> {
                val fd = try {
                    Os.open(path, flags, mode)
                } catch (error: ErrnoException) {
                    throw IOException("Local file open failed: $path", error)
                }
                try {
                    ParcelFileDescriptor.dup(fd)
                } finally {
                    runCatching { Os.close(fd) }
                }
            }
        }
    }

    @Deprecated("Use SuFileInputStream instead")
    @Throws(IOException::class)
    fun newInputStream(): InputStream =
        ParcelFileDescriptor.AutoCloseInputStream(FileUtils.openReadPipe(this, O_RDONLY, 0))

    @Deprecated("Use SuFileOutputStream instead")
    @Throws(IOException::class)
    fun newOutputStream(append: Boolean): OutputStream {
        val flags = O_CREAT or O_WRONLY or (if (append) O_APPEND else O_TRUNC)
        return ParcelFileDescriptor.AutoCloseOutputStream(FileUtils.openWritePipe(this, flags, 438))
    }

    @Throws(IOException::class)
    fun getCanonicalDirPath(): String {
        var canonicalPath = this.canonicalPath
        if (!canonicalPath.endsWith("/")) canonicalPath += "/"
        return canonicalPath
    }

    @Throws(IOException::class)
    fun getCanonicalFileIfChild(child: String): SuFile? {
        val parentCanonicalPath = getCanonicalDirPath()
        val childCanonicalPath = SuFile(this, child).canonicalPath
        if (childCanonicalPath.startsWith(parentCanonicalPath)) {
            return SuFile(childCanonicalPath)
        }
        return null
    }

    /**
     * Converts this [SuFile] instance to an [ExtFile] instance.
     *
     * This can be useful when you need to work with a file without requiring root privileges
     * or when interfacing with APIs that expect an [ExtFile].
     *
     * @return An [ExtFile] object representing the same file path as this [SuFile].
     */
    fun toExtFile(): ExtFile = ExtFile(this)

    companion object {
        const val TAG = "SuFile"
        const val PIPE_CAPACITY = 16 * 4096

        /**
         * Returns the default buffer size when working with buffered streams.
         */
        const val DEFAULT_BUFFER_SIZE: Int = 8 * 1024

        /**
         * Returns the default block size for forEachBlock().
         */
        const val DEFAULT_BLOCK_SIZE: Int = 4096

        /**
         * Returns the minimum block size for forEachBlock().
         */
        const val MINIMUM_BLOCK_SIZE: Int = 512


        fun String.toSuFile(): SuFile = SuFile(this)

        fun createDirectories(vararg file: SuFile): Boolean {
            for (f in file) {
                if (!f.mkdirs()) return false
            }
            return true
        }

        fun Int.toFormattedFileSize(): String = toDouble().toFormattedFileSize()

        fun Long.toFormattedFileSize(): String = toDouble().toFormattedFileSize()

        fun Float.toFormattedFileSize(): String = toDouble().toFormattedFileSize()

        fun Double.toFormattedFileSize(): String {
            if (this < 1024) return "$this B"

            val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
            var size = this
            val sizeRepresentation = SuFileSizeRepresentation.getRepresentation(size)
            val base = sizeRepresentation.base.toDouble()
            var unitIndex = 0

            while (size >= base && unitIndex < units.size - 1) {
                size /= base
                unitIndex++
            }

            return if (size == size.toLong().toDouble()) {
                String.format(Locale.getDefault(), "%.0f %s", size, units[unitIndex])
            } else {
                String.format(Locale.getDefault(), "%.2f %s", size, units[unitIndex])
            }
        }

        private fun <R> queryFallback(
            root: IFileManager.() -> R,
            nonRoot: () -> R,
        ): R {
            val fileManager = PlatformManager.fileManagerOrNull
            return try {
                if (PlatformManager.isAlive && fileManager != null && PlatformManager.platform.isPrivilegedRoot) {
                    root(fileManager)
                } else {
                    nonRoot()
                }
            } catch (_: Exception) {
                nonRoot()
            }
        }

        private fun mutationBoolean(
            root: IFileManager.() -> Boolean,
            nonRoot: () -> Boolean,
        ): Boolean {
            val manager = PlatformManager.fileManagerOrNull
            return when (PrivilegeRouting.select(PlatformManager.preferredPlatform.isPrivilegedRoot, PlatformManager.isAlive && manager != null)) {
                PrivilegeRouting.Backend.ROOT -> runCatching { root(requireNotNull(manager)) }.getOrDefault(false)
                PrivilegeRouting.Backend.LOCAL -> nonRoot()
                PrivilegeRouting.Backend.UNAVAILABLE -> false
            }
        }

        private fun mutationUnit(
            root: IFileManager.() -> Unit,
            nonRoot: () -> Unit,
        ) {
            val manager = PlatformManager.fileManagerOrNull
            when (PrivilegeRouting.select(PlatformManager.preferredPlatform.isPrivilegedRoot, PlatformManager.isAlive && manager != null)) {
                PrivilegeRouting.Backend.ROOT -> root(requireNotNull(manager))
                PrivilegeRouting.Backend.LOCAL -> nonRoot()
                PrivilegeRouting.Backend.UNAVAILABLE -> throw IllegalStateException("Privileged file service is unavailable; refusing local mutation fallback")
            }
        }
    }
}

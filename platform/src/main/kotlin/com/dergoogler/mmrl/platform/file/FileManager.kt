package com.dergoogler.mmrl.platform.file

import android.os.ParcelFileDescriptor
import android.system.OsConstants
import com.dergoogler.mmrl.platform.stub.IFileManager
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class FileManager : IFileManager.Stub() {
    init {
        System.loadLibrary("mmrl-file-manager")
    }

    private external fun nativeSetOwnerAt(root: String, relative: String, owner: Int, group: Int): Boolean
    private external fun nativeSetPermissionsAt(root: String, relative: String, mode: Int): Boolean
    private external fun nativeDeleteAt(root: String, relative: String): Boolean
    private external fun nativeOpenAt(root: String, relative: String, flags: Int, mode: Int): Int
    private external fun nativeModeAt(root: String, relative: String): Int
    private external fun nativeSizeAt(root: String, relative: String): Long
    private external fun nativeMtimeAt(root: String, relative: String): Long
    private external fun nativeListAt(root: String, relative: String): Array<String>?
    private external fun nativeAccessAt(root: String, relative: String, mode: Int): Boolean
    private external fun nativeMkdirAt(root: String, relative: String, recursive: Boolean): Boolean
    private external fun nativeCreateAt(root: String, relative: String): Boolean
    private external fun nativeRenameAt(
        sourceRoot: String,
        sourceRelative: String,
        targetRoot: String,
        targetRelative: String,
    ): Boolean
    private external fun nativeCopyAt(
        sourceRoot: String,
        sourceRelative: String,
        targetRoot: String,
        targetRelative: String,
        overwrite: Boolean,
    ): Boolean

    override fun deleteOnExit(path: String): Boolean {
        val resolution = PrivilegedPathPolicy.resolveWithRoot(path, PrivilegedPathPolicy.Access.MUTATE)
        if (nativeModeAt(resolution.root.toString(), resolution.relative.toString()) == 0) return false
        if (!ensureDeleteOnExitHook()) return false
        return deleteOnExitRegistry.register(
            DeleteOnExitRegistry.Entry(resolution.root.toString(), resolution.relative.toString()),
        )
    }

    override fun list(path: String): Array<String>? {
        val resolution = PrivilegedPathPolicy.resolveWithRoot(path, PrivilegedPathPolicy.Access.READ)
        return nativeListAt(resolution.root.toString(), resolution.relative.toString())
    }

    override fun length(path: String): Long {
        val resolution = PrivilegedPathPolicy.resolveWithRoot(path, PrivilegedPathPolicy.Access.READ)
        return nativeSizeAt(resolution.root.toString(), resolution.relative.toString()).coerceAtLeast(0L)
    }

    override fun stat(path: String): Long {
        val resolution = PrivilegedPathPolicy.resolveWithRoot(path, PrivilegedPathPolicy.Access.READ)
        return nativeMtimeAt(resolution.root.toString(), resolution.relative.toString()).coerceAtLeast(0L)
    }

    override fun delete(path: String): Boolean {
        val resolution = PrivilegedPathPolicy.resolveWithRoot(path, PrivilegedPathPolicy.Access.MUTATE)
        if (resolution.relative.toString().isEmpty()) return false
        return nativeDeleteAt(resolution.root.toString(), resolution.relative.toString())
    }

    override fun exists(path: String): Boolean = getMode(path) != 0

    override fun isDirectory(path: String): Boolean = OsConstants.S_ISDIR(getMode(path))

    override fun isFile(path: String): Boolean = OsConstants.S_ISREG(getMode(path))

    override fun isBlock(path: String): Boolean = OsConstants.S_ISBLK(getMode(path))

    override fun isCharacter(path: String): Boolean = OsConstants.S_ISCHR(getMode(path))

    override fun isSymlink(path: String): Boolean = OsConstants.S_ISLNK(getModeForInspection(path))

    override fun isNamedPipe(path: String): Boolean = OsConstants.S_ISFIFO(getMode(path))

    override fun isSocket(path: String): Boolean = OsConstants.S_ISSOCK(getMode(path))

    override fun mkdir(path: String): Boolean {
        val resolution = PrivilegedPathPolicy.resolveWithRoot(path, PrivilegedPathPolicy.Access.MUTATE)
        return resolution.relative.toString().isNotEmpty() &&
            nativeMkdirAt(resolution.root.toString(), resolution.relative.toString(), false)
    }

    override fun mkdirs(path: String): Boolean {
        val resolution = PrivilegedPathPolicy.resolveWithRoot(path, PrivilegedPathPolicy.Access.MUTATE)
        return resolution.relative.toString().isNotEmpty() &&
            nativeMkdirAt(resolution.root.toString(), resolution.relative.toString(), true)
    }

    override fun createNewFile(path: String): Boolean {
        val resolution = PrivilegedPathPolicy.resolveWithRoot(path, PrivilegedPathPolicy.Access.MUTATE)
        return resolution.relative.toString().isNotEmpty() &&
            nativeCreateAt(resolution.root.toString(), resolution.relative.toString())
    }

    override fun renameTo(target: String, dest: String): Boolean {
        val source = PrivilegedPathPolicy.resolveWithRoot(target, PrivilegedPathPolicy.Access.MUTATE)
        val destination = PrivilegedPathPolicy.resolveWithRoot(dest, PrivilegedPathPolicy.Access.MUTATE)
        if (source.relative.toString().isEmpty() || destination.relative.toString().isEmpty()) return false
        return nativeRenameAt(
            source.root.toString(),
            source.relative.toString(),
            destination.root.toString(),
            destination.relative.toString(),
        )
    }

    override fun copyTo(path: String, target: String, overwrite: Boolean) {
        val source = PrivilegedPathPolicy.resolveWithRoot(path, PrivilegedPathPolicy.Access.READ)
        val destination = PrivilegedPathPolicy.resolveWithRoot(target, PrivilegedPathPolicy.Access.MUTATE)
        if (destination.relative.toString().isEmpty() || !nativeCopyAt(
                source.root.toString(),
                source.relative.toString(),
                destination.root.toString(),
                destination.relative.toString(),
                overwrite,
            )
        ) {
            throw IOException("Unable to safely copy privileged path ${source.path} to ${destination.path}")
        }
    }

    override fun canExecute(path: String): Boolean = nativeAccess(path, OsConstants.X_OK)
    override fun canWrite(path: String): Boolean = nativeAccess(path, OsConstants.W_OK)
    override fun canRead(path: String): Boolean = nativeAccess(path, OsConstants.R_OK)
    override fun isHidden(path: String): Boolean = path.substringAfterLast('/').startsWith('.')

    private fun nativeAccess(path: String, mode: Int): Boolean {
        val resolution = PrivilegedPathPolicy.resolveWithRoot(path, PrivilegedPathPolicy.Access.READ)
        return nativeAccessAt(resolution.root.toString(), resolution.relative.toString(), mode)
    }

    private fun getModeForInspection(path: String): Int {
        val resolution = PrivilegedPathPolicy.resolveForInspectionWithRoot(path, PrivilegedPathPolicy.Access.READ)
        return nativeModeAt(resolution.root.toString(), resolution.relative.toString())
    }

    override fun setPermissions(path: String, mode: Int): Boolean {
        val resolution = PrivilegedPathPolicy.resolveWithRoot(path, PrivilegedPathPolicy.Access.MUTATE)
        return nativeSetPermissionsAt(resolution.root.toString(), resolution.relative.toString(), mode)
    }

    override fun setOwner(path: String, owner: Int, group: Int): Boolean {
        val resolution = PrivilegedPathPolicy.resolveWithRoot(path, PrivilegedPathPolicy.Access.MUTATE)
        return nativeSetOwnerAt(resolution.root.toString(), resolution.relative.toString(), owner, group)
    }

    override fun parcelFile(filePath: String): ParcelFileDescriptor =
        openFile(filePath, OsConstants.O_RDONLY, 0)

    override fun openFile(path: String, flags: Int, mode: Int): ParcelFileDescriptor {
        val plan = PrivilegedOpenPolicy.plan(flags, mode)
        val resolution = PrivilegedPathPolicy.resolveWithRoot(path, plan.access)
        val fd = nativeOpenAt(
            resolution.root.toString(),
            resolution.relative.toString(),
            plan.flags,
            plan.mode,
        )
        if (fd < 0) {
            throw IOException("Unable to safely open privileged path ${resolution.path} (errno=${-fd})")
        }
        return ParcelFileDescriptor.adoptFd(fd)
    }

    override fun getMode(path: String?): Int {
        val resolution = PrivilegedPathPolicy.resolveWithRoot(
            requireNotNull(path),
            PrivilegedPathPolicy.Access.READ,
        )
        return nativeModeAt(resolution.root.toString(), resolution.relative.toString())
    }

    private fun ensureDeleteOnExitHook(): Boolean {
        if (deleteOnExitHookInstalled.get()) return true
        synchronized(deleteOnExitHookInstalled) {
            if (deleteOnExitHookInstalled.get()) return true
            return runCatching {
                Runtime.getRuntime().addShutdownHook(
                    Thread({
                        deleteOnExitRegistry.drain { entry ->
                            nativeDeleteAt(entry.root, entry.relative)
                        }
                    }, "mmrl-delete-on-exit"),
                )
                deleteOnExitHookInstalled.set(true)
                true
            }.getOrDefault(false)
        }
    }

    private companion object {
        val deleteOnExitRegistry = DeleteOnExitRegistry()
        val deleteOnExitHookInstalled = AtomicBoolean(false)
    }
}

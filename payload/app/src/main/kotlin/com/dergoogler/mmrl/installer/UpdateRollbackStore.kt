package com.dergoogler.mmrl.installer

import android.content.Context
import com.dergoogler.mmrl.model.local.LocalModule
import com.dergoogler.mmrl.platform.file.SuFile
import com.dergoogler.mmrl.platform.file.inputStream
import com.dergoogler.mmrl.platform.model.ModId.Companion.moduleDir
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Atomic, operation-scoped rollback archives with bounded retention and storage use. */
@Singleton
class UpdateRollbackStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val storeGate = OperationStoreGate()
    private val root: File get() = File(context.filesDir, "update-rollbacks")

    suspend fun create(module: LocalModule, operationId: String = UUID.randomUUID().toString()): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                storeGate.exclusive {
                    // Validate the logical operation ID before pruning or mutating the store.
                    val opDir = OperationStoragePolicy.operationDirectory(root, operationId)
                    pruneLocked()
                    require(root.usableSpace >= MIN_FREE_BYTES) { "Not enough free space for rollback backup" }
                    val source = module.id.requireOperational().moduleDir
                    require(source.exists() && source.isDirectory) { "Installed module directory is unavailable" }
                    OperationStoragePolicy.deleteDirectChild(root, opDir)
                    require(opDir.mkdir()) { "Cannot create rollback operation directory" }
                    val artifactId = OperationStoragePolicy.safeOperationId("${module.id.id}\u0000${module.version}").removePrefix("op-")
                    val partial = opDir.resolve("$artifactId.zip.part")
                    val destination = opDir.resolve("$artifactId.zip")
                    try {
                        FileOutputStream(partial).use { raw ->
                            val buffered = raw.buffered()
                            val output = ZipOutputStream(buffered)
                            try {
                                var fileCount = 0
                                var totalBytes = 0L

                                fun append(file: SuFile, relative: String) {
                                    if (file.isSymlink()) return
                                    if (file.isDirectory) {
                                        file.listFiles().orEmpty().forEach { child ->
                                            append(child, if (relative.isBlank()) child.name else "$relative/${child.name}")
                                        }
                                        return
                                    }
                                    require(fileCount < MAX_FILES) { "Rollback backup contains too many files" }
                                    fileCount += 1
                                    require(relative.isNotBlank()) { "Rollback entry path is empty" }
                                    output.putNextEntry(ZipEntry(relative))
                                    file.inputStream().buffered().use { input ->
                                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            if (count == 0) continue
                                            totalBytes = OperationStoragePolicy.addWithinLimit(totalBytes, count, MAX_BYTES)
                                            output.write(buffer, 0, count)
                                        }
                                    }
                                    output.closeEntry()
                                }

                                append(source, "")
                                output.finish()
                                output.flush()
                                buffered.flush()
                                raw.fd.sync()
                            } finally {
                                output.close()
                            }
                        }
                        require(partial.length() > 0L) { "Rollback backup is empty" }
                        val existingBytes = managedDirectories().sumOf { dir ->
                            if (dir == opDir) 0L else OperationStoragePolicy.directChildSize(root, dir)
                        }
                        require(OperationStoragePolicy.canFit(existingBytes, partial.length(), MAX_TOTAL_BYTES)) {
                            "Rollback store quota would be exceeded"
                        }
                        AtomicFilePublication.move(partial, destination)
                        require(destination.setReadOnly() || !destination.canWrite()) { "Cannot make rollback archive immutable" }
                        destination
                    } catch (error: Throwable) {
                        runCatching { OperationStoragePolicy.deleteDirectChild(root, opDir) }
                            .exceptionOrNull()
                            ?.let(error::addSuppressed)
                        throw error
                    }
                }
            }.onFailure { Timber.e(it, "Unable to create rollback backup for ${module.id.id}") }
        }

    fun isManagedBackup(path: String?): Boolean = runCatching {
        val candidate = path?.let(::File) ?: return@runCatching false
        OperationStoragePolicy.requireManagedFile(root, candidate)
        true
    }.getOrDefault(false)

    /** Returns true when the managed rollback operation directory existed and was removed. */
    suspend fun delete(path: String?): Boolean = withContext(Dispatchers.IO) {
        storeGate.exclusive {
            val candidate = path?.let(::File) ?: return@exclusive false
            val managedFile = runCatching { OperationStoragePolicy.requireManagedFile(root, candidate) }
                .getOrNull() ?: return@exclusive false
            val operationDirectory = managedFile.parentFile ?: return@exclusive false
            OperationStoragePolicy.deleteDirectChild(root, operationDirectory)
        }
    }

    private fun pruneLocked() {
        ensureRoot()
        val now = System.currentTimeMillis()
        managedDirectories().forEach { dir ->
            if (OperationStoragePolicy.isExpired(dir.lastModified(), now, MAX_AGE_MS)) {
                OperationStoragePolicy.deleteDirectChild(root, dir)
            }
        }
        val dirs = managedDirectories().sortedByDescending(File::lastModified)
        dirs.drop(MAX_BACKUPS).forEach { dir -> OperationStoragePolicy.deleteDirectChild(root, dir) }
        var total = managedDirectories().sumOf { OperationStoragePolicy.directChildSize(root, it) }
        if (total > MAX_TOTAL_BYTES) {
            managedDirectories().sortedBy(File::lastModified).forEach { dir ->
                if (total <= MAX_TOTAL_BYTES) return@forEach
                val bytes = OperationStoragePolicy.directChildSize(root, dir)
                if (OperationStoragePolicy.deleteDirectChild(root, dir)) total -= bytes
            }
        }
    }

    private fun ensureRoot(): File = root.also { directory ->
        if (!directory.exists()) require(directory.mkdirs()) { "Cannot create rollback store root" }
        require(directory.isDirectory) { "Rollback store root is not a directory" }
    }

    private fun managedDirectories(): List<File> = ensureRoot().listFiles().orEmpty()
        .filter { it.isDirectory || java.nio.file.Files.isSymbolicLink(it.toPath()) }
        .map { OperationStoragePolicy.requireDirectChild(root, it) }


    companion object {
        private const val MAX_BACKUPS = 20
        private const val MAX_FILES = 20_000
        private const val MAX_BYTES = 512L * 1024L * 1024L
        private const val MAX_TOTAL_BYTES = 1024L * 1024L * 1024L
        private const val MIN_FREE_BYTES = 256L * 1024L * 1024L
        private const val MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    }
}

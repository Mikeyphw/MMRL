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
    private val root: File get() = File(context.filesDir, "update-rollbacks").apply { mkdirs() }

    suspend fun create(module: LocalModule, operationId: String = UUID.randomUUID().toString()): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                storeGate.exclusive {
                    pruneLocked()
                    require(root.usableSpace >= MIN_FREE_BYTES) { "Not enough free space for rollback backup" }
                    val source = module.id.requireOperational().moduleDir
                    require(source.exists() && source.isDirectory) { "Installed module directory is unavailable" }
                    val opDir = root.resolve(OperationStoragePolicy.safeOperationId(operationId))
                    if (opDir.exists()) opDir.deleteRecursively()
                    require(opDir.mkdirs()) { "Cannot create rollback operation directory" }
                    val safeVersion = safe(module.version)
                    val partial = opDir.resolve("${safe(module.id.id)}-$safeVersion.zip.part")
                    val destination = opDir.resolve("${safe(module.id.id)}-$safeVersion.zip")
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
                        val existingBytes = root.walkTopDown().filter(File::isFile).filterNot { it == partial }.sumOf(File::length)
                        require(OperationStoragePolicy.canFit(existingBytes, partial.length(), MAX_TOTAL_BYTES)) { "Rollback store quota would be exceeded" }
                        AtomicFilePublication.move(partial, destination)
                        require(destination.setReadOnly() || !destination.canWrite()) { "Cannot make rollback archive immutable" }
                        destination
                    } catch (error: Throwable) {
                        opDir.deleteRecursively()
                        throw error
                    }
                }
            }.onFailure { Timber.e(it, "Unable to create rollback backup for ${module.id.id}") }
        }

    fun isManagedBackup(path: String?): Boolean = runCatching {
        path?.let(::File)?.canonicalFile?.toPath()?.startsWith(root.canonicalFile.toPath()) == true
    }.getOrDefault(false)

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        storeGate.exclusive {
            path?.takeIf(::isManagedBackup)?.let { managedPath ->
                val file = File(managedPath)
                val opDir = file.parentFile?.takeIf { it.parentFile?.canonicalFile == root.canonicalFile }
                if (opDir != null) opDir.deleteRecursively() else file.delete()
            }
        }
    }

    private fun pruneLocked() {
        val now = System.currentTimeMillis()
        root.listFiles().orEmpty().filter(File::isDirectory).forEach { dir ->
            if (OperationStoragePolicy.isExpired(dir.lastModified(), now, MAX_AGE_MS)) dir.deleteRecursively()
        }
        val dirs = root.listFiles().orEmpty().filter(File::isDirectory).sortedByDescending(File::lastModified)
        dirs.drop(MAX_BACKUPS).forEach(File::deleteRecursively)
        var total = root.walkTopDown().filter(File::isFile).sumOf(File::length)
        if (total > MAX_TOTAL_BYTES) {
            root.listFiles().orEmpty().filter(File::isDirectory).sortedBy(File::lastModified).forEach { dir ->
                if (total <= MAX_TOTAL_BYTES) return@forEach
                val bytes = dir.walkTopDown().filter(File::isFile).sumOf(File::length)
                if (dir.deleteRecursively()) total -= bytes
            }
        }
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")


    companion object {
        private const val MAX_BACKUPS = 20
        private const val MAX_FILES = 20_000
        private const val MAX_BYTES = 512L * 1024L * 1024L
        private const val MAX_TOTAL_BYTES = 1024L * 1024L * 1024L
        private const val MIN_FREE_BYTES = 256L * 1024L * 1024L
        private const val MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    }
}

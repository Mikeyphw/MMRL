package com.dergoogler.mmrl.installer

import android.content.Context
import com.dergoogler.mmrl.model.local.LocalModule
import com.dergoogler.mmrl.platform.file.SuFile
import com.dergoogler.mmrl.platform.file.inputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRollbackStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val root: File get() = File(context.filesDir, "update-rollbacks").apply { mkdirs() }

        suspend fun create(module: LocalModule): Result<File> =
            withContext(Dispatchers.IO) {
                runCatching {
                    prune()
                    val source = SuFile("/data/adb/modules/${module.id.id}")
                    require(source.exists() && source.isDirectory) { "Installed module directory is unavailable" }
                    val safeVersion = module.version.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val destination = File(root, "${module.id.id}-$safeVersion-${System.currentTimeMillis()}.zip")
                    try {
                        ZipOutputStream(FileOutputStream(destination).buffered()).use { output ->
                            var fileCount = 0
                            var totalBytes = 0L

                            fun append(file: SuFile, relative: String) {
                                if (fileCount >= MAX_FILES) error("Rollback backup contains too many files")
                                if (file.isSymlink()) return
                                if (file.isDirectory) {
                                    file.listFiles().orEmpty().forEach { child ->
                                        append(
                                            child,
                                            if (relative.isBlank()) child.name else "$relative/${child.name}",
                                        )
                                    }
                                    return
                                }
                                val size = file.length().coerceAtLeast(0L)
                                totalBytes += size
                                if (totalBytes > MAX_BYTES) {
                                    error("Rollback backup exceeds ${MAX_BYTES / 1024 / 1024} MiB")
                                }
                                fileCount += 1
                                output.putNextEntry(ZipEntry(relative))
                                file.inputStream().buffered().use { it.copyTo(output) }
                                output.closeEntry()
                            }

                            append(source, "")
                        }
                        require(destination.length() > 0L) { "Rollback backup is empty" }
                        destination
                    } catch (error: Throwable) {
                        destination.delete()
                        throw error
                    }
                }.onFailure { Timber.e(it, "Unable to create rollback backup for ${module.id.id}") }
            }

        fun isManagedBackup(path: String?): Boolean =
            path?.let(::File)?.canonicalPath?.startsWith(root.canonicalPath + File.separator) == true

        suspend fun delete(path: String?) =
            withContext(Dispatchers.IO) {
                if (isManagedBackup(path)) runCatching { File(path!!).delete() }
            }

        private fun prune() {
            root.listFiles().orEmpty().sortedByDescending(File::lastModified).drop(MAX_BACKUPS).forEach {
                runCatching { it.delete() }
            }
        }

        companion object {
            private const val MAX_BACKUPS = 20
            private const val MAX_FILES = 20_000
            private const val MAX_BYTES = 512L * 1024L * 1024L
        }
    }

package com.dergoogler.mmrl.ash.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.dergoogler.mmrl.ash.model.AshBundledModuleMetadata
import com.dergoogler.mmrl.ash.model.AshInstallMode
import com.dergoogler.mmrl.service.DownloadReceiptStore
import com.dergoogler.mmrl.installer.AtomicFilePublication
import com.dergoogler.mmrl.installer.OperationStoreGate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AshModuleInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bundledModuleProvider: AshBundledModuleProvider,
    private val receipts: DownloadReceiptStore,
) {
    private val stagingGate = OperationStoreGate()

    data class PreparedInstall(
        val uri: Uri,
        val mode: AshInstallMode,
        val metadata: AshBundledModuleMetadata,
        val sha256: String,
        val stagingOperationId: String,
    )

    suspend fun prepare(mode: AshInstallMode): PreparedInstall = withContext(Dispatchers.IO) {
        stagingGate.exclusive {
            val metadata = bundledModuleProvider.metadata()
            val root = File(context.cacheDir, "ashrexcue-staging").apply { mkdirs() }
            prune(root)
            require(root.usableSpace >= MIN_FREE_BYTES) { "Insufficient free space for AshReXcue staging" }
            val operationId = UUID.randomUUID().toString()
            val directory = File(root, operationId).apply {
                require(mkdirs()) { "Unable to create AshReXcue staging directory" }
            }
            val partial = File(directory, "artifact.part")
            val module = File(directory, "artifact.zip")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            try {
                context.assets.open(AshBundledModuleProvider.ASH_MODULE_ZIP_ASSET).use { input ->
                    FileOutputStream(partial).use { raw ->
                        raw.buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count > 0) {
                                    total = Math.addExact(total, count.toLong())
                                    require(total <= MAX_ARTIFACT_BYTES) { "Bundled AshReXcue module exceeds staging limit" }
                                    digest.update(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                }
                            }
                            output.flush()
                        }
                        raw.fd.sync()
                    }
                }
                require(total > 0L) { "Bundled AshReXcue module ZIP is empty" }
                require(totalStagedBytes(root) <= MAX_STORE_BYTES) { "AshReXcue staging quota exceeded" }
                AtomicFilePublication.move(partial, module)
                require(module.setReadOnly() || !module.canWrite()) { "Unable to make AshReXcue staging artifact immutable" }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    module,
                )
                receipts.record(
                    uri = uri,
                    sourceUrl = "asset:///${AshBundledModuleProvider.ASH_MODULE_ZIP_ASSET}",
                    sha256 = sha256,
                    size = total,
                    destinationPath = module.absolutePath,
                )
                PreparedInstall(
                    uri = uri,
                    mode = mode,
                    metadata = metadata,
                    sha256 = sha256,
                    stagingOperationId = operationId,
                )
            } catch (error: Throwable) {
                directory.deleteRecursively()
                throw error
            }
        }
    }

    private fun prune(root: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        root.listFiles { file -> file.isDirectory }.orEmpty()
            .filter { it.lastModified() < cutoff }
            .forEach(File::deleteRecursively)
        val staged = root.listFiles { file -> file.isDirectory }.orEmpty().sortedByDescending(File::lastModified)
        var bytes = staged.sumOf(::directoryBytes)
        staged.asReversed().forEach { dir ->
            if (bytes <= MAX_STORE_BYTES) return@forEach
            val size = directoryBytes(dir)
            if (dir.deleteRecursively()) bytes -= size
        }
    }

    private fun totalStagedBytes(root: File): Long =
        root.listFiles { file -> file.isDirectory }.orEmpty().sumOf(::directoryBytes)

    private fun directoryBytes(directory: File): Long =
        directory.walkTopDown().filter(File::isFile).sumOf(File::length)

    companion object {
        private const val MAX_ARTIFACT_BYTES = 256L * 1024L * 1024L
        private const val MAX_STORE_BYTES = 512L * 1024L * 1024L
        private const val MIN_FREE_BYTES = 128L * 1024L * 1024L
        private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}

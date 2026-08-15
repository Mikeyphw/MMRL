package com.dergoogler.mmrl.installer

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.dergoogler.mmrl.service.DownloadReceiptStore
import com.dergoogler.mmrl.service.DownloadReusePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Operation-scoped, immutable staging with byte/age/free-space quotas and digest re-verification. */
@Singleton
class OperationStagingStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val receipts: DownloadReceiptStore,
) {
    private val storeGate = OperationStoreGate()
    private val leasedOperationIds = ConcurrentHashMap.newKeySet<String>()
    private val root: File get() = File(context.filesDir, "operation-staging")

    suspend fun stage(source: Uri, operationId: String = UUID.randomUUID().toString()): StagedArtifact =
        withContext(Dispatchers.IO) {
            storeGate.exclusive {
                val safeOperationId = OperationStoragePolicy.safeOperationId(operationId)
                pruneLocked()
                require(root.usableSpace >= MIN_FREE_BYTES) { "Not enough free space for a verified staging copy" }
                require(leasedOperationIds.add(safeOperationId)) { "Operation staging is already leased: $safeOperationId" }
                val opDir = root.resolve(safeOperationId)
                if (opDir.exists()) opDir.deleteRecursively()
                require(opDir.mkdirs()) { "Cannot create operation staging directory" }
                val partial = opDir.resolve("artifact.zip.part")
                val final = opDir.resolve("artifact.zip")
                try {
                    val digest = context.contentResolver.openInputStream(source)?.buffered()?.use { input ->
                        val md = java.security.MessageDigest.getInstance("SHA-256")
                        var total = 0L
                        FileOutputStream(partial).use { raw ->
                            raw.buffered().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (count == 0) continue
                                    total = Math.addExact(total, count.toLong())
                                    require(total <= MAX_ARTIFACT_BYTES) { "Artifact exceeds staging size limit" }
                                    md.update(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                }
                                output.flush()
                            }
                            raw.fd.sync()
                        }
                        ArtifactDigest.Digest(md.digest().joinToString("") { "%02x".format(it) }, total)
                    } ?: throw IOException("Cannot read source URI: $source")
                require(digest.size > 0L) { "Cannot stage an empty artifact" }
                val existingBytes = root.walkTopDown().filter(File::isFile).filterNot { it == partial }.sumOf(File::length)
                require(OperationStoragePolicy.canFit(existingBytes, digest.size, MAX_TOTAL_BYTES)) { "Staging store quota would be exceeded" }
                AtomicFilePublication.move(partial, final)
                require(final.setReadOnly() || !final.canWrite()) { "Cannot make staged artifact immutable" }
                val receipt = receipts.verify(source)
                val receiptForStagedBytes = receipt?.takeIf { DownloadReusePolicy.matches(it, digest) }
                val provenance = ArtifactProvenance(
                    sourceUri = source.toString(),
                    sourceUrl = receiptForStagedBytes?.sourceUrl,
                    sha256 = digest.sha256,
                    size = digest.size,
                )
                StagedArtifact(operationId, final, provenance)
                } catch (error: Throwable) {
                    leasedOperationIds.remove(safeOperationId)
                    opDir.deleteRecursively()
                    throw error
                }
            }
        }

    suspend fun verify(staged: StagedArtifact): Boolean = withContext(Dispatchers.IO) {
        val digest = runCatching { ArtifactDigest.of(staged.file) }.getOrNull() ?: return@withContext false
        digest.size == staged.provenance.size && digest.sha256.equals(staged.provenance.sha256, ignoreCase = true)
    }

    suspend fun release(operationId: String) = withContext(Dispatchers.IO) {
        storeGate.exclusive {
            val safeOperationId = OperationStoragePolicy.safeOperationId(operationId)
            leasedOperationIds.remove(safeOperationId)
            root.resolve(safeOperationId).deleteRecursively()
        }
    }

    private fun pruneLocked() {
        if (!root.exists()) root.mkdirs()
        val now = System.currentTimeMillis()
        root.listFiles().orEmpty().filter(File::isDirectory).forEach { dir ->
            if (OperationStoragePolicy.canPruneOperation(dir.name, leasedOperationIds) &&
                OperationStoragePolicy.isExpired(dir.lastModified(), now, MAX_AGE_MS)
            ) {
                dir.deleteRecursively()
            }
        }
        var total = root.walkTopDown().filter(File::isFile).sumOf(File::length)
        if (total > MAX_TOTAL_BYTES) {
            root.listFiles().orEmpty().filter(File::isDirectory).sortedBy(File::lastModified).forEach { dir ->
                if (total <= MAX_TOTAL_BYTES) return@forEach
                if (!OperationStoragePolicy.canPruneOperation(dir.name, leasedOperationIds)) return@forEach
                val bytes = dir.walkTopDown().filter(File::isFile).sumOf(File::length)
                if (dir.deleteRecursively()) total -= bytes
            }
        }
    }


    data class StagedArtifact(
        val operationId: String,
        val file: File,
        val provenance: ArtifactProvenance,
    ) {
        val uri: Uri get() = file.toUri()
    }

    companion object {
        const val MAX_ARTIFACT_BYTES = 1_073_741_824L
        const val MAX_TOTAL_BYTES = 2_147_483_648L
        const val MIN_FREE_BYTES = 268_435_456L
        const val MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}

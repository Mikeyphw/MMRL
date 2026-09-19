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
                // Resolve/validate the logical ID before pruning or any other store mutation.
                val opDir = OperationStoragePolicy.operationDirectory(root, operationId)
                val storageId = opDir.name
                pruneLocked()
                require(root.usableSpace >= MIN_FREE_BYTES) { "Not enough free space for a verified staging copy" }
                require(leasedOperationIds.add(storageId)) { "Operation staging is already leased: $operationId" }
                try {
                    OperationStoragePolicy.deleteDirectChild(root, opDir)
                    require(opDir.mkdir()) { "Cannot create operation staging directory" }
                    val partial = opDir.resolve("artifact.zip.part")
                    val final = opDir.resolve("artifact.zip")
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
                    val existingBytes = managedDirectories().sumOf { dir ->
                        if (dir == opDir) 0L else OperationStoragePolicy.directChildSize(root, dir)
                    }
                    require(OperationStoragePolicy.canFit(existingBytes, digest.size, MAX_TOTAL_BYTES)) {
                        "Staging store quota would be exceeded"
                    }
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
                    leasedOperationIds.remove(storageId)
                    runCatching { OperationStoragePolicy.deleteDirectChild(root, opDir) }
                        .exceptionOrNull()
                        ?.let(error::addSuppressed)
                    throw error
                }
            }
        }

    suspend fun verify(staged: StagedArtifact): Boolean = withContext(Dispatchers.IO) {
        val digest = runCatching { ArtifactDigest.of(staged.file) }.getOrNull() ?: return@withContext false
        digest.size == staged.provenance.size && digest.sha256.equals(staged.provenance.sha256, ignoreCase = true)
    }

    /** Returns true when a managed staging directory existed and was removed. */
    suspend fun release(operationId: String): Boolean = withContext(Dispatchers.IO) {
        storeGate.exclusive {
            val opDir = OperationStoragePolicy.operationDirectory(root, operationId)
            leasedOperationIds.remove(opDir.name)
            OperationStoragePolicy.deleteDirectChild(root, opDir)
        }
    }

    private fun pruneLocked() {
        ensureRoot()
        val now = System.currentTimeMillis()
        managedDirectories().forEach { dir ->
            if (OperationStoragePolicy.canPruneOperation(dir.name, leasedOperationIds) &&
                OperationStoragePolicy.isExpired(dir.lastModified(), now, MAX_AGE_MS)
            ) {
                OperationStoragePolicy.deleteDirectChild(root, dir)
            }
        }
        var total = managedDirectories().sumOf { OperationStoragePolicy.directChildSize(root, it) }
        if (total > MAX_TOTAL_BYTES) {
            managedDirectories().sortedBy(File::lastModified).forEach { dir ->
                if (total <= MAX_TOTAL_BYTES) return@forEach
                if (!OperationStoragePolicy.canPruneOperation(dir.name, leasedOperationIds)) return@forEach
                val bytes = OperationStoragePolicy.directChildSize(root, dir)
                if (OperationStoragePolicy.deleteDirectChild(root, dir)) total -= bytes
            }
        }
    }

    private fun ensureRoot(): File = root.also { directory ->
        if (!directory.exists()) require(directory.mkdirs()) { "Cannot create operation staging root" }
        require(directory.isDirectory) { "Operation staging root is not a directory" }
    }

    private fun managedDirectories(): List<File> = ensureRoot().listFiles().orEmpty()
        .filter { it.isDirectory || java.nio.file.Files.isSymbolicLink(it.toPath()) }
        .map { OperationStoragePolicy.requireDirectChild(root, it) }

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

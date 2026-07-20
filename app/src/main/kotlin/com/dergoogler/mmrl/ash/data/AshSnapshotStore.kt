package com.dergoogler.mmrl.ash.data

import android.content.Context
import android.util.AtomicFile
import com.dergoogler.mmrl.ash.model.AshSnapshotIntegrity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AshSnapshotStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val root = File(context.filesDir, "ashrexcue").apply { mkdirs() }
    private val primary = AtomicFile(File(root, "last-successful-snapshot.json"))
    private val backup = AtomicFile(File(root, "last-good-snapshot.json"))
    private val corruptDirectory = File(root, "corrupt").apply { mkdirs() }

    data class Entry(
        val savedAt: Long,
        val moduleStateRaw: String,
        val snapshotRaw: String,
    )

    data class ReadResult(
        val entry: Entry? = null,
        val events: List<String> = emptyList(),
    )

    suspend fun read(): ReadResult = withContext(Dispatchers.IO) {
        if (!primary.baseFile.isFile && !backup.baseFile.isFile) return@withContext ReadResult()
        val events = mutableListOf<String>()
        val primaryResult = readAtomic(primary)
        if (primaryResult != null) {
            if (primaryResult.second) {
                events += "cache-migrated"
                persist(primaryResult.first)
            }
            return@withContext ReadResult(primaryResult.first, events)
        }

        if (primary.baseFile.isFile) {
            quarantineCorrupt(primary)
            events += "cache-corrupt"
        }
        val backupResult = readAtomic(backup)
        if (backupResult != null) {
            persist(backupResult.first)
            events += "cache-recovered"
            if (backupResult.second) events += "cache-migrated"
            return@withContext ReadResult(backupResult.first, events)
        }
        if (backup.baseFile.isFile) {
            quarantineCorrupt(backup)
            events += "cache-corrupt"
        }
        ReadResult(events = events.distinct())
    }

    suspend fun write(
        moduleStateRaw: String,
        snapshotRaw: String,
        savedAt: Long = System.currentTimeMillis() / 1_000L,
    ) = withContext(Dispatchers.IO) {
        val normalizedModuleState = JSONObject(moduleStateRaw).toString()
        val normalizedSnapshot = JSONObject(snapshotRaw).toString()
        persist(Entry(savedAt, normalizedModuleState, normalizedSnapshot))
    }

    private fun persist(entry: Entry) {
        val payload = encode(entry)
        writeAtomic(primary, payload)
        runCatching { writeAtomic(backup, payload) }
    }

    private fun readAtomic(source: AtomicFile): Pair<Entry, Boolean>? = runCatching {
        val root = JSONObject(source.readFully().toString(Charsets.UTF_8))
        val envelopeVersion = root.optInt("envelopeVersion", 1)
        require(envelopeVersion in 1..CURRENT_ENVELOPE_VERSION) {
            "Unsupported snapshot envelope $envelopeVersion"
        }
        val moduleStateRaw = root.getJSONObject("moduleState").toString()
        val snapshotRaw = root.getJSONObject("snapshot").toString()
        val entry = Entry(
            savedAt = root.optLong("savedAt"),
            moduleStateRaw = moduleStateRaw,
            snapshotRaw = snapshotRaw,
        )
        if (envelopeVersion >= CURRENT_ENVELOPE_VERSION) {
            require(root.optString("checksumAlgorithm") == AshSnapshotIntegrity.ALGORITHM) {
                "Unsupported snapshot checksum"
            }
            require(root.optString("checksum") == checksum(entry)) {
                "Snapshot checksum mismatch"
            }
        }
        entry to (envelopeVersion < CURRENT_ENVELOPE_VERSION)
    }.getOrNull()

    private fun encode(entry: Entry): ByteArray = JSONObject()
        .put("envelopeVersion", CURRENT_ENVELOPE_VERSION)
        .put("savedAt", entry.savedAt)
        .put("checksumAlgorithm", AshSnapshotIntegrity.ALGORITHM)
        .put("checksum", checksum(entry))
        .put("moduleState", JSONObject(entry.moduleStateRaw))
        .put("snapshot", JSONObject(entry.snapshotRaw))
        .toString()
        .toByteArray(Charsets.UTF_8)

    private fun checksum(entry: Entry): String = AshSnapshotIntegrity.checksum(
        savedAt = entry.savedAt,
        moduleStateRaw = entry.moduleStateRaw,
        snapshotRaw = entry.snapshotRaw,
    )

    private fun writeAtomic(target: AtomicFile, payload: ByteArray) {
        val output = target.startWrite()
        try {
            output.write(payload)
            output.flush()
            target.finishWrite(output)
        } catch (error: Throwable) {
            target.failWrite(output)
            throw error
        }
    }

    private fun quarantineCorrupt(source: AtomicFile) {
        corruptDirectory.mkdirs()
        val baseFile = source.baseFile
        val target = File(corruptDirectory, "${baseFile.name}.${System.currentTimeMillis()}.corrupt")
        runCatching { baseFile.copyTo(target, overwrite = true) }
        runCatching { source.delete() }
        corruptDirectory.listFiles().orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(MAX_CORRUPT_FILES)
            .forEach(File::delete)
    }

    private companion object {
        const val CURRENT_ENVELOPE_VERSION = 2
        const val MAX_CORRUPT_FILES = 5
    }
}

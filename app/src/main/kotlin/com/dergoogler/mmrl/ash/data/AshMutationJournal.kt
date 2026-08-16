package com.dergoogler.mmrl.ash.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Crash-safe journal for root-side AshReXcue mutations. */
class AshMutationJournal(context: Context) {
    private val root = File(context.filesDir, "ashrexcue/mutation-journal").apply { mkdirs() }

    data class Entry(
        val operationId: String,
        val stage: Stage,
        val commandHash: String,
        val writtenAt: Long,
        val message: String = "",
    )

    enum class Stage { PREPARING, ACTIVE, COMMITTING, COMMITTED, OUTCOME_UNKNOWN }

    @Synchronized
    fun write(operationId: String, stage: Stage, command: String, message: String = ""): Entry {
        val entry = Entry(
            operationId = safe(operationId),
            stage = stage,
            commandHash = sha256(command),
            writtenAt = System.currentTimeMillis(),
            message = message.take(512),
        )
        val json = JSONObject()
            .put("operationId", entry.operationId)
            .put("stage", entry.stage.name)
            .put("commandHash", entry.commandHash)
            .put("writtenAt", entry.writtenAt)
            .put("message", entry.message)
            .toString()
        writeAtomic(file(entry.operationId), json)
        prune()
        return entry
    }

    @Synchronized
    fun committed(operationId: String, command: String, message: String = "") =
        write(operationId, Stage.COMMITTED, command, message)

    @Synchronized
    fun interrupted(): List<Entry> = root.listFiles().orEmpty()
        .mapNotNull { file -> read(file) }
        .filter { it.stage in setOf(Stage.PREPARING, Stage.ACTIVE, Stage.COMMITTING, Stage.OUTCOME_UNKNOWN) }
        .sortedBy(Entry::writtenAt)

    private fun read(file: File): Entry? = runCatching {
        val json = JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8))
        Entry(
            operationId = json.getString("operationId"),
            stage = Stage.valueOf(json.getString("stage")),
            commandHash = json.getString("commandHash"),
            writtenAt = json.optLong("writtenAt"),
            message = json.optString("message"),
        )
    }.getOrNull()

    private fun writeAtomic(target: File, content: String) {
        target.parentFile?.mkdirs()
        val atomic = AtomicFile(target)
        val out = atomic.startWrite()
        try {
            out.write(content.toByteArray(Charsets.UTF_8))
            out.flush()
            atomic.finishWrite(out)
        } catch (error: Throwable) {
            atomic.failWrite(out)
            throw error
        }
    }

    private fun file(operationId: String): File = File(root, safe(operationId) + ".json")
    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(160)
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun prune() { root.listFiles().orEmpty().sortedByDescending(File::lastModified).drop(128).forEach(File::delete) }
}

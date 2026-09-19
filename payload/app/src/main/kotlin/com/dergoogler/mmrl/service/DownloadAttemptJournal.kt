package com.dergoogler.mmrl.service

import java.io.File
import java.util.Properties
import java.util.UUID

/** Durable, attempt-scoped recovery journal for DownloadService. */
internal class DownloadAttemptJournal(private val root: File) {
    enum class Phase { QUEUED, DOWNLOADING, PUBLISHING, SUCCEEDED, FAILED, CANCELLED }
    data class Record(val operationId: String, val attemptId: String, val phase: Phase, val temporaryPath: String, val destinationPath: String)

    init { root.mkdirs() }

    @Synchronized fun write(record: Record) {
        require(record.operationId.isNotBlank())
        validateAttemptId(record.attemptId)
        root.mkdirs()
        val target = file(record.attemptId)
        val staged = File(root, ".${record.attemptId}.tmp")
        Properties().apply {
            setProperty("operationId", record.operationId)
            setProperty("attemptId", record.attemptId)
            setProperty("phase", record.phase.name)
            setProperty("temporaryPath", record.temporaryPath)
            setProperty("destinationPath", record.destinationPath)
        }.let { props -> staged.outputStream().buffered().use { props.store(it, null) } }
        if (!staged.renameTo(target)) { staged.copyTo(target, overwrite = true); check(staged.delete()) }
    }

    @Synchronized fun transition(attemptId: String, phase: Phase): Boolean {
        val current = read(attemptId) ?: return false
        if (current.phase in terminal) return current.phase == phase
        write(current.copy(phase = phase)); return true
    }

    @Synchronized fun read(attemptId: String): Record? = runCatching {
        validateAttemptId(attemptId)
        val props = Properties().apply { file(attemptId).inputStream().buffered().use(::load) }
        Record(props.getProperty("operationId"), props.getProperty("attemptId"), Phase.valueOf(props.getProperty("phase")), props.getProperty("temporaryPath", ""), props.getProperty("destinationPath", ""))
    }.getOrNull()

    @Synchronized fun recoverable(): List<Record> = root.listFiles { f -> f.isFile && f.name.endsWith(".properties") }.orEmpty().mapNotNull { f -> read(f.name.removeSuffix(".properties")) }.filter { it.phase !in terminal }
    @Synchronized fun remove(attemptId: String): Boolean {
        validateAttemptId(attemptId)
        val target = file(attemptId)
        return !target.exists() || target.delete()
    }

    private fun file(attemptId: String): File {
        validateAttemptId(attemptId)
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, "$attemptId.properties").canonicalFile
        require(candidate.parentFile == canonicalRoot) { "Attempt journal path escaped its root" }
        return candidate
    }

    private fun validateAttemptId(attemptId: String) {
        require(runCatching { UUID.fromString(attemptId).toString() == attemptId.lowercase() }.getOrDefault(false)) {
            "Attempt id must be a canonical UUID"
        }
    }
    companion object { val terminal = setOf(Phase.SUCCEEDED, Phase.FAILED, Phase.CANCELLED) }
}

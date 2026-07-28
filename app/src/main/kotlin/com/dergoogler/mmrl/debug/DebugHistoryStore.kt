package com.dergoogler.mmrl.debug

import android.content.Context
import java.io.File
import java.util.Locale

/** Stores a small, redacted local history of Debug Workbench probe runs. */
class DebugHistoryStore(
    context: Context,
    private val maxSnapshots: Int = DEFAULT_MAX_SNAPSHOTS,
) {
    private val historyFile = File(File(context.filesDir, DIRECTORY).apply { mkdirs() }, FILE_NAME)

    fun record(results: List<DebugProbeResult>): DebugHistorySnapshot {
        val snapshot = DebugHistorySnapshot(
            createdAtMillis = System.currentTimeMillis(),
            entries = results.map { result ->
                DebugHistoryEntry(
                    id = result.id,
                    title = result.title,
                    group = result.group,
                    status = result.status,
                    summary = DebugRedactor.redact(result.summary),
                )
            },
        )
        val snapshots = (listOf(snapshot) + loadRecent())
            .distinctBy { it.createdAtMillis }
            .sortedByDescending { it.createdAtMillis }
            .take(maxSnapshots.coerceAtLeast(1))
        writeSnapshots(snapshots)
        return snapshot
    }

    fun loadRecent(): List<DebugHistorySnapshot> {
        if (!historyFile.isFile) return emptyList()
        return historyFile.readLines()
            .mapNotNull { line -> line.toEntryOrNull() }
            .groupBy { it.createdAtMillis }
            .map { (createdAtMillis, rows) ->
                DebugHistorySnapshot(
                    createdAtMillis = createdAtMillis,
                    entries = rows.sortedBy { it.id }.map { it.entry },
                )
            }
            .sortedByDescending { it.createdAtMillis }
            .take(maxSnapshots.coerceAtLeast(1))
    }

    fun clear(): Boolean {
        return !historyFile.exists() || historyFile.delete()
    }

    private fun writeSnapshots(snapshots: List<DebugHistorySnapshot>) {
        historyFile.parentFile?.mkdirs()
        historyFile.writeText(
            snapshots.flatMap { snapshot ->
                snapshot.entries.map { entry ->
                    listOf(
                        VERSION,
                        snapshot.createdAtMillis.toString(),
                        entry.id.escaped(),
                        entry.title.escaped(),
                        entry.group.name,
                        entry.status.name,
                        entry.summary.escaped(),
                    ).joinToString("\t")
                }
            }.joinToString(separator = "\n", postfix = if (snapshots.isEmpty()) "" else "\n"),
        )
    }

    private fun String.escaped(): String = buildString {
        this@escaped.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
    }

    private fun String.unescaped(): String = buildString {
        var escaping = false
        this@unescaped.forEach { char ->
            if (escaping) {
                append(
                    when (char) {
                        't' -> '\t'
                        'n' -> '\n'
                        'r' -> '\r'
                        '\\' -> '\\'
                        else -> char
                    },
                )
                escaping = false
            } else if (char == '\\') {
                escaping = true
            } else {
                append(char)
            }
        }
        if (escaping) append('\\')
    }

    private fun String.toEntryOrNull(): PersistedHistoryEntry? {
        val parts = split('\t')
        if (parts.size != FIELD_COUNT || parts[0] != VERSION) return null
        val createdAtMillis = parts[1].toLongOrNull() ?: return null
        val group = runCatching { DebugProbeGroup.valueOf(parts[4]) }.getOrNull() ?: return null
        val status = runCatching { DebugProbeStatus.valueOf(parts[5]) }.getOrNull() ?: return null
        val entry = DebugHistoryEntry(
            id = parts[2].unescaped(),
            title = parts[3].unescaped(),
            group = group,
            status = status,
            summary = DebugRedactor.redact(parts[6].unescaped()),
        )
        return PersistedHistoryEntry(createdAtMillis, entry)
    }

    private data class PersistedHistoryEntry(
        val createdAtMillis: Long,
        val entry: DebugHistoryEntry,
    ) {
        val id: String get() = entry.id
    }

    companion object {
        const val DEFAULT_MAX_SNAPSHOTS = 10
        private const val DIRECTORY = "debug-workbench"
        private const val FILE_NAME = "probe-history.tsv"
        private const val VERSION = "v1"
        private const val FIELD_COUNT = 7

        fun compare(
            currentResults: List<DebugProbeResult>,
            previous: DebugHistorySnapshot?,
        ): DebugHistoryComparison? {
            previous ?: return null
            val previousById = previous.entries.associateBy { it.id }
            val currentEntries = currentResults.map { result ->
                DebugHistoryEntry(
                    id = result.id,
                    title = result.title,
                    group = result.group,
                    status = result.status,
                    summary = DebugRedactor.redact(result.summary),
                )
            }
            val deltas = currentEntries.map { current ->
                val old = previousById[current.id]
                DebugHistoryDelta(
                    id = current.id,
                    title = current.title,
                    previousStatus = old?.status,
                    currentStatus = current.status,
                    currentSummary = current.summary,
                )
            }
            return DebugHistoryComparison(
                previousCreatedAtMillis = previous.createdAtMillis,
                currentCount = currentEntries.size,
                improved = deltas.filter { it.improved },
                regressed = deltas.filter { it.regressed },
                newlyFailing = deltas.filter { it.newlyFailing },
                fixedSinceLast = deltas.filter { it.fixedSinceLast },
                unchangedCount = deltas.count { it.previousStatus == it.currentStatus },
            )
        }
    }
}

data class DebugHistorySnapshot(
    val createdAtMillis: Long,
    val entries: List<DebugHistoryEntry>,
)

data class DebugHistoryEntry(
    val id: String,
    val title: String,
    val group: DebugProbeGroup,
    val status: DebugProbeStatus,
    val summary: String,
)

data class DebugHistoryDelta(
    val id: String,
    val title: String,
    val previousStatus: DebugProbeStatus?,
    val currentStatus: DebugProbeStatus,
    val currentSummary: String,
) {
    val newlyFailing: Boolean
        get() = previousStatus == null && currentStatus.isProblem

    val fixedSinceLast: Boolean
        get() = previousStatus?.isProblem == true && currentStatus == DebugProbeStatus.PASS

    val improved: Boolean
        get() = previousStatus != null && currentStatus.severity < previousStatus.severity

    val regressed: Boolean
        get() = previousStatus != null && currentStatus.severity > previousStatus.severity
}

data class DebugHistoryComparison(
    val previousCreatedAtMillis: Long,
    val currentCount: Int,
    val improved: List<DebugHistoryDelta>,
    val regressed: List<DebugHistoryDelta>,
    val newlyFailing: List<DebugHistoryDelta>,
    val fixedSinceLast: List<DebugHistoryDelta>,
    val unchangedCount: Int,
) {
    val hasChanges: Boolean
        get() = improved.isNotEmpty() || regressed.isNotEmpty() || newlyFailing.isNotEmpty() || fixedSinceLast.isNotEmpty()
}

object DebugHistoryFormatter {
    fun comparisonText(comparison: DebugHistoryComparison?): String {
        if (comparison == null) return "No previous run is available yet. This run will become the comparison baseline."
        if (!comparison.hasChanges) {
            return "No status changes since previous run. ${comparison.unchangedCount}/${comparison.currentCount} checks are unchanged."
        }
        return buildString {
            append("Compared with previous run: ")
            append(comparison.regressed.size)
            append(" regressed, ")
            append(comparison.improved.size)
            append(" improved, ")
            append(comparison.newlyFailing.size)
            append(" newly failing, ")
            append(comparison.fixedSinceLast.size)
            append(" fixed.")
            comparison.regressed.take(3).forEach { delta ->
                append("\nRegressed: ")
                append(delta.title)
                append(" (")
                append(delta.previousStatus)
                append(" → ")
                append(delta.currentStatus)
                append(")")
            }
            comparison.fixedSinceLast.take(3).forEach { delta ->
                append("\nFixed: ")
                append(delta.title)
            }
        }
    }

    fun historyText(history: List<DebugHistorySnapshot>): String = buildString {
        appendLine("MMRL Debug Workbench history")
        appendLine("redacted=true")
        history.forEachIndexed { index, snapshot ->
            appendLine()
            appendLine("Run ${index + 1}: ${snapshot.createdAtMillis}")
            snapshot.entries.forEach { entry ->
                appendLine("- [${entry.status}] ${entry.title}: ${DebugRedactor.redact(entry.summary)}")
            }
        }
    }

    fun historyJson(history: List<DebugHistorySnapshot>): String = buildString {
        appendLine("[")
        history.forEachIndexed { snapshotIndex, snapshot ->
            appendLine("  {")
            appendLine("    \"createdAtMillis\": ${snapshot.createdAtMillis},")
            appendLine("    \"results\": [")
            snapshot.entries.forEachIndexed { entryIndex, entry ->
                appendLine("      {")
                appendLine("        \"id\": ${entry.id.json()},")
                appendLine("        \"title\": ${entry.title.json()},")
                appendLine("        \"group\": ${entry.group.name.json()},")
                appendLine("        \"status\": ${entry.status.name.json()},")
                appendLine("        \"summary\": ${DebugRedactor.redact(entry.summary).json()}")
                appendLine("      }${if (entryIndex == snapshot.entries.lastIndex) "" else ","}")
            }
            appendLine("    ]")
            appendLine("  }${if (snapshotIndex == history.lastIndex) "" else ","}")
        }
        appendLine("]")
    }

    private fun String.json(): String = buildString {
        append('"')
        this@json.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 32) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0').uppercase(Locale.US))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }
}

private val DebugProbeStatus.severity: Int
    get() = when (this) {
        DebugProbeStatus.PASS -> 0
        DebugProbeStatus.SKIPPED -> 0
        DebugProbeStatus.UNKNOWN -> 1
        DebugProbeStatus.WARN -> 2
        DebugProbeStatus.FAIL -> 3
    }

private val DebugProbeStatus.isProblem: Boolean
    get() = this == DebugProbeStatus.WARN || this == DebugProbeStatus.FAIL

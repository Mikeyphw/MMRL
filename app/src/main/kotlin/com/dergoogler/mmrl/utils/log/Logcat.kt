package com.dergoogler.mmrl.utils.log

import android.content.Context
import com.dergoogler.mmrl.App
import com.dergoogler.mmrl.ext.getLogPath
import com.dergoogler.mmrl.ext.shareFile
import com.dergoogler.mmrl.utils.log.LogText.Companion.toLogPriority

object Logcat {
    const val FILE_NAME = "mmrl_log"

    private val context by lazy { App.context }
    private val uid by lazy { context.applicationInfo.uid }
    private val logFile by lazy { context.getLogPath(FILE_NAME) }

    fun getCurrent(): List<String> =
        try {
            val command =
                arrayOf(
                    "logcat",
                    "-d",
                    "-v",
                    "threadtime",
                    "--uid",
                    uid.toString(),
                    "-t",
                    MAX_LOGCAT_LINES.toString(),
                )

            val process = Runtime.getRuntime().exec(command)
            val result =
                process.inputStream.use { stream ->
                    stream
                        .bufferedReader()
                        .lineSequence()
                        .filterNot { it.startsWith("------") }
                        .map { it.take(MAX_LOG_LINE_CHARS) }
                        .take(MAX_LOGCAT_LINES)
                        .toList()
                }

            process.waitFor()
            result
        } catch (e: Exception) {
            emptyList()
        }

    fun readLogs(): List<LogText> =
        if (logFile.exists()) {
            val logs = mutableListOf<LogText>()
            readPersistedLogText().lineSequence().toList().takeLast(MAX_PERSISTED_LOG_LINES).forEach { text ->
                runCatching {
                    LogText.parse(text)
                }.onSuccess {
                    logs.add(it)
                }.onFailure {
                    val last = logs.last()
                    val new = last.copy(message = "${last.message}\n$text")
                    logs[logs.size - 1] = new
                }
            }

            logs.toList()
        } else {
            emptyList()
        }

    fun writeLogs(logs: List<LogText>) {
        if (logs.isEmpty()) return

        val texts = logs.joinToString(separator = "\n", postfix = "\n")
        logFile.appendText(texts)
        trimPersistedLogIfNeeded()
    }

    private fun trimPersistedLogIfNeeded() {
        if (!logFile.exists() || logFile.length() <= MAX_PERSISTED_LOG_BYTES) return
        val trimmed = readPersistedLogText().lineSequence()
            .toList()
            .takeLast(MAX_PERSISTED_LOG_LINES)
            .joinToString(separator = "\n", postfix = "\n")
        logFile.writeText(trimmed.takeLast(MAX_PERSISTED_LOG_BYTES.toInt()))
    }

    private fun readPersistedLogText(): String {
        if (!logFile.exists()) return ""
        if (logFile.length() <= MAX_PERSISTED_LOG_BYTES) return logFile.readText()
        val bytes = ByteArray(MAX_PERSISTED_LOG_BYTES.toInt())
        java.io.RandomAccessFile(logFile, "r").use { input ->
            input.seek((input.length() - bytes.size).coerceAtLeast(0))
            input.readFully(bytes)
        }
        return bytes.toString(Charsets.UTF_8)
    }

    fun shareLogs(context: Context) {
        context.shareFile(logFile, "text/plain")
    }

    fun List<String>.toLogTextList(): List<LogText> {
        val tmp = map { it.split(": ", limit = 2) }
        val tags = tmp.map { it.first() }.distinct()
        val logs =
            tags.map { tag ->
                val message =
                    tmp
                        .filter {
                            it.first() == tag
                        }.map { it.last() }
                        .reduceOrNull { b, e ->
                            "$b\n$e"
                        } ?: ""

                tag.toLogText().copy(message = message.trim())
            }

        return logs
    }

    private fun String.toLogText(): LogText =
        try {
            split(": ", limit = 2).let { list ->
                val item =
                    list
                        .first()
                        .split(" ")
                        .filter { it != "" }

                LogText(
                    priority = item[4].toLogPriority(),
                    time = "${item[0]} ${item[1]}",
                    process = "${item[2]}-${item[3]}",
                    tag = item[5],
                    message = list.last(),
                )
            }
        } catch (e: Exception) {
            LogText(0, "", "", "", "")
        }

    private const val MAX_LOGCAT_LINES = 1_000
    private const val MAX_LOG_LINE_CHARS = 8_192
    private const val MAX_PERSISTED_LOG_LINES = 2_000
    private const val MAX_PERSISTED_LOG_BYTES = 512L * 1024L
}

package com.dergoogler.mmrl.ash.root

import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class AshCtlExecutor {
    private val modulePaths = listOf(
        File("/data/adb/modules/AshLooper/ashrexcuectl"),
        File("/data/adb/modules_update/AshLooper/ashrexcuectl"),
        File("/data/adb/modules/AshReXcue/ashrexcuectl"),
        File("/data/adb/modules_update/AshReXcue/ashrexcuectl"),
        File("/data/adb/modules/ashrexcue/ashrexcuectl"),
        File("/data/adb/modules_update/ashrexcue/ashrexcuectl"),
    )

    fun moduleAvailable(): Boolean = modulePaths.any { it.isFile && it.canExecute() }

    fun serviceInfo(): String = JSONObject()
        .put("ok", true)
        .put("uid", android.system.Os.getuid())
        .put("pid", android.system.Os.getpid())
        .put("api", 1)
        .put("transport", "libsu-rootservice-aidl")
        .toString()

    fun status(): String = execute("status")
    fun modules(): String = execute("modules")
    fun quarantine(): String = execute("quarantine")
    fun activity(limit: Int): String = execute("activity", limit.coerceIn(1, 200).toString())
    fun settings(): String = execute("settings")
    fun pendingSettings(): String = execute("pending-settings")

    fun setSetting(key: String, value: String): String {
        requireSetting(key, value)
        return execute("set-setting", key, value)
    }

    fun setSettings(keys: Array<out String>, values: Array<out String>): String {
        require(keys.isNotEmpty()) { "No settings were supplied" }
        require(keys.size == values.size) { "Setting keys and values do not match" }
        require(keys.size <= SETTING_KEYS.size) { "Too many settings were supplied" }
        val arguments = buildList {
            keys.indices.forEach { index ->
                requireSetting(keys[index], values[index])
                add(keys[index])
                add(values[index])
            }
        }
        return execute("set-settings", *arguments.toTypedArray())
    }

    fun setTrust(folder: String, trust: String): String {
        requireSafeFolder(folder)
        require(trust in TRUST_VALUES) { "Unsupported trust category" }
        return execute("set-trust", folder, trust)
    }

    fun restoreOne(folder: String): String {
        requireSafeFolder(folder)
        return execute("restore", "one", folder)
    }

    fun restoreHalf(): String = execute("restore", "half")
    fun restoreAll(): String = execute("restore", "all")
    fun completeTrial(): String = execute("complete-trial")
    fun rollbackTrial(): String = execute("rollback-trial")
    fun discardPendingSettings(): String = execute("discard-pending-settings")
    fun exportDiagnostics(): String = execute("diagnostic-export", timeoutSeconds = 120)

    private fun execute(
        command: String,
        vararg arguments: String,
        timeoutSeconds: Long = 15,
    ): String {
        val ctl = modulePaths.firstOrNull { it.isFile && it.canExecute() }
            ?: return errorJson("AshReXcue module is not installed or ashrexcuectl is not executable")
        val process = runCatching {
            ProcessBuilder(listOf(ctl.absolutePath, command) + arguments)
                .redirectErrorStream(true)
                .start()
        }.getOrElse { return errorJson(it.message ?: "Unable to start AshReXcue control process") }

        val readerExecutor = Executors.newSingleThreadExecutor()
        val outputFuture = readerExecutor.submit<String> {
            process.inputStream.bufferedReader().use { it.readText() }
        }
        return try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return errorJson("AshReXcue operation timed out")
            }
            val output = runCatching { outputFuture.get(5, TimeUnit.SECONDS) }
                .getOrElse { return errorJson(it.message ?: "Unable to read AshReXcue response") }
                .trim()
            if (output.isBlank()) return errorJson("AshReXcue returned an empty response")
            val jsonText = output
                .lineSequence()
                .map { it.trim() }
                .lastOrNull { it.startsWith("{") && it.endsWith("}") }
                ?: output
            val json = runCatching { JSONObject(jsonText) }.getOrElse {
                return errorJson("AshReXcue returned malformed JSON: ${output.take(240)}")
            }
            if (process.exitValue() != 0 && !json.has("ok")) {
                json.put("ok", false)
            }
            json.toString()
        } finally {
            outputFuture.cancel(true)
            readerExecutor.shutdownNow()
            process.destroy()
        }
    }

    private fun requireSafeFolder(folder: String) {
        require(FOLDER_PATTERN.matches(folder)) { "Invalid module folder" }
    }

    private fun requireSetting(key: String, value: String) {
        require(key in SETTING_KEYS) { "Unsupported setting: $key" }
        require(value.length <= 512 && value.none { it == '\u0000' || it == '\n' || it == '\r' }) {
            "Invalid setting value"
        }
    }

    private fun errorJson(message: String): String = JSONObject()
        .put("ok", false)
        .put("message", message)
        .toString()

    private companion object {
        val FOLDER_PATTERN = Regex("^[A-Za-z0-9._-]{1,128}$")
        val TRUST_VALUES = setOf("protected", "trusted", "normal", "suspect")
        val SETTING_KEYS = setOf(
            "mode",
            "threshold",
            "timeout",
            "timeout_min",
            "timeout_max",
            "stability_time",
            "failure_threshold",
            "check_interval",
            "restart_limit",
            "boot_ready_consecutive",
            "extra_stability",
            "systemui_process",
            "monitored_processes",
            "missing_process_action",
            "boot_animation_required",
            "ce_storage_required",
            "launcher_check_required",
            "launcher_wait",
            "launcher_focus_required",
            "fail_boot_file_required",
            "fail_boot_file_path",
            "fail_boot_file_wait",
            "first_boot_grace",
            "ota_grace_time",
        )
    }
}

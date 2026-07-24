package com.dergoogler.mmrl.ash.root

import android.content.Context
import com.dergoogler.mmrl.ash.data.AshBundledModuleProvider
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import java.util.concurrent.TimeUnit

internal class AshCtlExecutor(
    private val context: Context? = null,
    private val moduleLocator: AshModuleLocator = AshModuleLocator(),
) {
    fun moduleAvailable(): Boolean {
        val inspection = moduleLocator.inspect()
        val jq = repairBundledJqIfNeeded(inspection)
        return inspection.controlScript != null && jq.executable
    }

    fun moduleState(): String {
        val inspection = moduleLocator.inspect()
        val properties = inspection.properties
        val jq = repairBundledJqIfNeeded(inspection)
        return JSONObject()
            .put("ok", true)
            .put("installed", inspection.installed)
            .put("active", inspection.active)
            .put("folder", inspection.folder)
            .put("id", properties["id"].orEmpty())
            .put("name", properties["name"].orEmpty())
            .put("version", properties["version"].orEmpty())
            .put("versionCode", properties["versionCode"]?.toIntOrNull() ?: 0)
            .put("source", inspection.source)
            .put("controlAvailable", inspection.controlScript != null)
            .put("disabled", inspection.disabled)
            .put("removalPending", inspection.removalPending)
            .put("updatePending", inspection.updatePending)
            .put("jqPresent", jq.present)
            .put("jqExecutable", jq.executable)
            .put("jqRepaired", jq.repaired)
            .put("jqRepairMessage", jq.message)
            .toString()
    }

    fun serviceInfo(): String = JSONObject()
        .put("ok", true)
        .put("uid", android.system.Os.getuid())
        .put("pid", android.system.Os.getpid())
        .put("api", 2)
        .put("transport", "libsu-rootservice-aidl")
        .toString()

    fun capabilities(): String = execute("capabilities", timeoutSeconds = 10)
    fun snapshot(activityLimit: Int): String =
        execute("snapshot", activityLimit.coerceIn(1, 200).toString(), timeoutSeconds = 70)
    fun releaseGate(): String = execute("release-gate", timeoutSeconds = 30)
    fun status(): String = execute("status", timeoutSeconds = 70)
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
    fun restoreBatch(folders: Array<out String>): String {
        require(folders.isNotEmpty()) { "No restoration modules were supplied" }
        require(folders.size <= 64) { "Too many restoration modules were supplied" }
        folders.forEach(::requireSafeFolder)
        return execute("restore", "selected", folders.joinToString(","))
    }
    fun executeRecoveryPlan(
        planId: String,
        recoveryRevision: String,
        folders: Array<out String>,
    ): String {
        require(PLAN_ID_PATTERN.matches(planId)) { "Invalid recovery plan identifier" }
        require(REVISION_PATTERN.matches(recoveryRevision)) { "Invalid recovery revision" }
        require(folders.isNotEmpty()) { "No recovery plan modules were supplied" }
        require(folders.size <= MAX_RECOVERY_PLAN_MODULES) { "Recovery plan exceeds the safety limit" }
        require(folders.toSet().size == folders.size) { "Recovery plan contains duplicate modules" }
        folders.forEach(::requireSafeFolder)
        return execute("restore", "planned", planId, recoveryRevision, folders.joinToString(","))
    }
    fun restoreAll(): String = execute("restore", "all")
    fun completeTrial(): String = execute("complete-trial")
    fun rollbackTrial(): String = execute("rollback-trial")
    fun discardPendingSettings(): String = execute("discard-pending-settings")
    fun exportDiagnostics(): String = execute("diagnostic-export", timeoutSeconds = 120)
    fun repairState(): String = execute("repair-state", timeoutSeconds = 70)

    private fun execute(
        command: String,
        vararg arguments: String,
        timeoutSeconds: Long = 25,
    ): String {
        val inspection = moduleLocator.inspect()
        val ctl = inspection.controlScript
            ?: return errorJson("AshReXcue module is not installed or its control script could not be found")
        val jq = repairBundledJqIfNeeded(inspection)
        if (!jq.executable) {
            return errorJson(
                "AshReXcue needs the bundled jq runtime. ${jq.message} Reinstall the bundled module from MMRL if automatic repair cannot restore it.",
            )
        }
        val process = runCatching {
            ProcessBuilder(listOf(SYSTEM_SHELL, ctl.absolutePath, command) + arguments)
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
            val jsonText = AshJsonOutput.extractObject(output) ?: output
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


    private fun repairBundledJqIfNeeded(inspection: AshModuleLocator.Inspection): JqRuntimeState {
        val moduleDirectory = inspection.directory
            ?: return JqRuntimeState(message = "No installed AshReXcue module directory was found.")
        val jq = File(moduleDirectory, JQ_RELATIVE_PATH)
        if (jq.isFile) {
            chmodExecutable(jq)
            return JqRuntimeState(
                present = true,
                executable = jq.canExecute(),
                repaired = jq.canExecute(),
                message = if (jq.canExecute()) {
                    "Bundled jq is executable."
                } else {
                    "Bundled jq exists but could not be made executable."
                },
            )
        }
        if (!inspection.active) {
            return JqRuntimeState(
                present = false,
                executable = false,
                repaired = false,
                message = "A staged AshReXcue module is waiting for reboot, but the active bundled jq runtime is missing.",
            )
        }
        val appContext = context ?: return JqRuntimeState(
            present = false,
            executable = false,
            repaired = false,
            message = "Bundled jq is missing and the root service cannot access app assets to repair it.",
        )
        return runCatching {
            extractBundledJq(appContext, jq)
            chmodExecutable(jq)
            JqRuntimeState(
                present = jq.isFile,
                executable = jq.canExecute(),
                repaired = jq.isFile && jq.canExecute(),
                message = if (jq.isFile && jq.canExecute()) {
                    "Bundled jq was restored from the packaged AshReXcue module."
                } else {
                    "Bundled jq could not be restored from the packaged AshReXcue module."
                },
            )
        }.getOrElse { error ->
            JqRuntimeState(
                present = jq.isFile,
                executable = jq.canExecute(),
                repaired = false,
                message = error.message ?: "Bundled jq repair failed.",
            )
        }
    }

    private fun extractBundledJq(context: Context, target: File) {
        val parent = requireNotNull(target.parentFile) { "Bundled jq target has no parent directory" }
        parent.mkdirs()
        val temporary = File(parent, "${target.name}.repair-${android.system.Os.getpid()}")
        if (temporary.exists()) temporary.delete()
        context.assets.open(AshBundledModuleProvider.ASH_MODULE_ZIP_ASSET).use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.removePrefix("./")
                    if (!entry.isDirectory && name == JQ_RELATIVE_PATH) {
                        temporary.outputStream().use(zip::copyTo)
                        if (target.exists() && !target.delete()) {
                            temporary.delete()
                            error("Unable to replace stale bundled jq runtime")
                        }
                        if (!temporary.renameTo(target)) {
                            temporary.copyTo(target, overwrite = true)
                            temporary.delete()
                        }
                        return
                    }
                }
            }
        }
        error("Packaged AshReXcue module does not contain $JQ_RELATIVE_PATH")
    }

    private fun chmodExecutable(file: File) {
        file.setReadable(true, false)
        file.setWritable(true, true)
        file.setExecutable(true, false)
        runCatching {
            ProcessBuilder(SYSTEM_CHMOD, "755", file.absolutePath)
                .redirectErrorStream(true)
                .start()
                .waitFor(5, TimeUnit.SECONDS)
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

    private data class JqRuntimeState(
        val present: Boolean = false,
        val executable: Boolean = false,
        val repaired: Boolean = false,
        val message: String = "Bundled jq runtime was not inspected.",
    )

    private companion object {
        const val SYSTEM_SHELL = "/system/bin/sh"
        const val SYSTEM_CHMOD = "/system/bin/chmod"
        const val JQ_RELATIVE_PATH = "jq/jq"
        const val MAX_RECOVERY_PLAN_MODULES = 8
        val FOLDER_PATTERN = Regex("^[A-Za-z0-9._-]{1,128}$")
        val PLAN_ID_PATTERN = Regex("^[A-Za-z0-9._-]{1,128}$")
        val REVISION_PATTERN = Regex("^[A-Za-z0-9._:-]{1,128}$")
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

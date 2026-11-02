package com.dergoogler.mmrl.lsposed

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Phase 03: explicit, bounded, read-only Vector CLI diagnostics.
 *
 * Only an authenticated answer from Vector's CLI proves the daemon responded.
 * Neither module installation, daemon IPC, nor an enabled module proves any
 * individual app process was hooked. Legacy LSPosed has no compatible public
 * status protocol here and is intentionally left unverified.
 */
object LsposedRuntime {
    data class ModuleState(val packageName: String, val enabled: Boolean)

    data class Snapshot(
        val frameworks: List<LsposedDiscovery.FrameworkModule>,
        val vectorInstalled: Boolean,
        val vectorEnabledAtNextBoot: Boolean,
        val daemonResponded: Boolean,
        val frameworkVersion: String?,
        val versionCode: Long?,
        val apiVersion: Int?,
        val reportedEnabledCount: Int?,
        val modules: List<ModuleState>?,
        val detail: String,
    )

    // An exact, fixed command: no interpolated package names, scope, or write operations.
    // Vector's module-shipped CLI internally authenticates to its daemon socket.
    private const val CLI = "/data/adb/modules/zygisk_vector/cli"
    private const val MAX_OUTPUT_BYTES = 131072
    private const val TIMEOUT_SECONDS = 10L
    private const val MAX_MODULES = 2048

    /** Call from Dispatchers.IO, only in response to an explicit button press. */
    fun inspect(): Snapshot {
        val frameworks = LsposedDiscovery.inspectFrameworkModules()
        val vector = frameworks.firstOrNull { it.id == "zygisk_vector" }
        if (vector == null) {
            return Snapshot(frameworks, false, false, false, null, null, null, null, null,
                "Vector module not returned by MMRL's provider; legacy LSPosed runtime not verified.")
        }
        if (vector.state != "ENABLE") {
            return Snapshot(frameworks, true, false, false, null, null, null, null, null,
                "Vector is installed but provider reports ${vector.state}; runtime not probed.")
        }
        return try {
            val status = response(runCli("status"))
            val data = status.optJSONObject("data")
                ?: throw IOException("Vector CLI status has no structured data")
            val version = data.optString("Framework Version").takeIf { it.isNotBlank() }?.take(100)
            val code = optionalLong(data, "Version Code")
            val api = optionalInt(data, "API Version")
            val count = optionalInt(data, "Enabled Modules")
            val moduleResult = runCatching { parseModules(response(runCli("modules ls"))) }
            Snapshot(frameworks, true, true, true, version, code, api, count,
                moduleResult.getOrNull(),
                if (moduleResult.isSuccess) {
                    "Vector daemon answered its CLI. Process injection and active hooks remain unverified."
                } else {
                    "Vector daemon answered status, but module configuration query is unavailable. " +
                        "Injection and hooks remain unverified."
                })
        } catch (e: Exception) {
            Snapshot(frameworks, true, true, false, null, null, null, null, null,
                "Vector installed/enabled, but CLI status could not be verified (${e.javaClass.simpleName}). " +
                    "Do not infer daemon failure from a denied root request or unsupported CLI.")
        }
    }

    private fun optionalInt(obj: JSONObject, key: String): Int? =
        if (obj.has(key) && !obj.isNull(key)) obj.optInt(key) else null

    private fun optionalLong(obj: JSONObject, key: String): Long? =
        if (obj.has(key) && !obj.isNull(key)) obj.optLong(key) else null

    private fun response(json: String): JSONObject {
        val root = JSONObject(json)
        if (!root.has("success") || !root.optBoolean("success")) {
            throw IOException("Vector CLI did not acknowledge request")
        }
        return root
    }

    private fun parseModules(root: JSONObject): List<ModuleState> {
        val rows: JSONArray = root.optJSONArray("data")
            ?: throw IOException("Vector module listing has no array")
        if (rows.length() > MAX_MODULES) throw IOException("Vector module list too large")
        val modules = ArrayList<ModuleState>(rows.length())
        for (i in 0 until rows.length()) {
            val item = rows.optJSONObject(i) ?: throw IOException("Invalid Vector module row")
            val pkg = item.optString("PACKAGE")
            if (!pkg.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) {
                throw IOException("Invalid Vector module identifier")
            }
            val state = item.optString("STATUS")
            if (state != "enabled" && state != "disabled") {
                throw IOException("Unknown Vector module state")
            }
            modules.add(ModuleState(pkg, state == "enabled"))
        }
        return modules.sortedBy { it.packageName }
    }

    /** Never execute arbitrary commands or pass user-controlled arguments. */
    private fun runCli(command: String): String {
        require(command == "status" || command == "modules ls")
        val process = ProcessBuilder("su", "-c", "$CLI --json $command")
            .redirectErrorStream(true)
            .start()
        val executor = Executors.newSingleThreadExecutor { work ->
            Thread(work, "vector-status-stream").apply { isDaemon = true }
        }
        try {
            val output = executor.submit(Callable {
                process.inputStream.use { input ->
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (bytes.size() + read > MAX_OUTPUT_BYTES) {
                            throw IOException("Vector CLI response exceeds limit")
                        }
                        bytes.write(buffer, 0, read)
                    }
                    bytes.toString(Charsets.UTF_8.name())
                }
            })
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IOException("Vector CLI timed out")
            }
            val text = output.get(2L, TimeUnit.SECONDS).trim()
            if (process.exitValue() != 0) throw IOException("Vector CLI returned a nonzero exit code")
            if (text.isEmpty()) throw IOException("Vector CLI returned empty output")
            return text
        } finally {
            process.destroyForcibly()
            executor.shutdownNow()
        }
    }
}

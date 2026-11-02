package com.dergoogler.mmrl.lsposed

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Phase 06: human-confirmed Vector *configuration* manager. No claim about active hooks.
 * All calls must occur on Dispatchers.IO. Root module state is never modified.
 * Never uses scope set, arbitrary CLI input, global settings, or background writes.
 */
object VectorManager {
    data class Module(val packageName: String, val enabled: Boolean, val label: String, val format: String)
    data class Scope(val packageName: String, val userId: Int)
    data class Overview(val modules: List<Module>, val frameworkVersion: String, val apiVersion: Int)
    enum class Action { ENABLE, DISABLE, ADD_SCOPE, REMOVE_SCOPE }
    data class Change(
        val modulePackage: String,
        val action: Action,
        val expectedEnabled: Boolean,
        val targetPackage: String? = null,
        val expectedScope: List<Scope>? = null,
    )
    data class Result(val overview: Overview, val scope: List<Scope>, val detail: String)

    private const val CLI = "/data/adb/modules/zygisk_vector/cli"
    private const val MAX_BYTES = 131072
    private const val TIMEOUT = 12L
    private val PKG = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")

    private fun valid(pkg: String) = pkg.length <= 180 && PKG.matches(pkg)

    /** Bounded fixed executable + validated tokens; no user-provided shell syntax. */
    private fun cli(vararg parts: String): JSONObject {
        val args = parts.toList()
        val permitted = when {
            args == listOf("status") || args == listOf("modules", "ls") -> true
            args.size == 3 && args[0] == "modules" && args[1] in setOf("enable", "disable") -> valid(args[2])
            args.size == 3 && args[0] == "scope" && args[1] == "ls" -> valid(args[2])
            args.size == 4 && args[0] == "scope" && args[1] in setOf("add", "rm") ->
                valid(args[2]) && args[3].endsWith("/0") && valid(args[3].removeSuffix("/0"))
            else -> false
        }
        if (!permitted) throw IllegalArgumentException("Vector operation is not allowlisted")
        val command = "$CLI --json ${args.joinToString(" ")}" // strictly validated tokens
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val worker = Executors.newSingleThreadExecutor { task ->
            Thread(task, "mmrl-vector-cli").apply { isDaemon = true }
        }
        try {
            val read = worker.submit(Callable {
                process.inputStream.use { stream ->
                    val bytes = ByteArrayOutputStream()
                    val chunk = ByteArray(4096)
                    while (true) {
                        val n = stream.read(chunk)
                        if (n < 0) break
                        if (bytes.size() + n > MAX_BYTES) throw IOException("Vector response is too large")
                        bytes.write(chunk, 0, n)
                    }
                    bytes.toString(Charsets.UTF_8.name())
                }
            })
            if (!process.waitFor(TIMEOUT, TimeUnit.SECONDS)) throw IOException("Vector CLI timed out")
            val output = read.get(2L, TimeUnit.SECONDS).trim()
            if (process.exitValue() != 0) throw IOException("Vector CLI failed or root denied")
            val response = JSONObject(output)
            if (!response.optBoolean("success", false)) {
                throw IOException("Vector rejected operation: " + response.optString("error").take(150))
            }
            return response
        } finally {
            process.destroyForcibly()
            worker.shutdownNow()
        }
    }

    private fun supported(): Pair<String, Int> {
        val data = cli("status").optJSONObject("data")
            ?: throw IOException("Vector status missing")
        val api = data.optInt("API Version", 0)
        if (api < 102) throw IOException("Vector API 102 or higher required for this manager")
        return data.optString("Framework Version").take(90) to api
    }

    fun inspect(context: Context): Overview {
        val (version, api) = supported()
        val rows = cli("modules", "ls").optJSONArray("data")
            ?: throw IOException("Vector module inventory missing")
        if (rows.length() > 512) throw IOException("Vector inventory too large")
        val apks = LsposedDiscovery.scanInstalledApks(context.applicationContext)
            .modules.associateBy { it.packageName }
        val found = mutableListOf<Module>()
        val seen = mutableSetOf<String>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: throw IOException("Malformed module row")
            val pkg = row.optString("PACKAGE")
            val status = row.optString("STATUS")
            if (!valid(pkg) || !seen.add(pkg) || status !in setOf("enabled", "disabled")) {
                throw IOException("Unrecognized or duplicate Vector module")
            }
            val apk = apks[pkg]
            found += Module(pkg, status == "enabled", apk?.label ?: pkg,
                apk?.format ?: "Unclassified APK (compatibility unverified)")
        }
        return Overview(found.sortedBy { it.packageName }, version, api)
    }

    fun scopes(modulePackage: String): List<Scope> {
        require(valid(modulePackage))
        supported()
        return readScope(modulePackage)
    }

    private fun readScope(modulePackage: String): List<Scope> {
        val data = cli("scope", "ls", modulePackage).optJSONArray("data")
            ?: throw IOException("Vector scope inventory missing")
        if (data.length() > 1000) throw IOException("Scope inventory too large")
        val entries = mutableListOf<Scope>()
        for (i in 0 until data.length()) {
            val row = data.optJSONObject(i) ?: throw IOException("Invalid scope row")
            val pkg = row.optString("APP_PACKAGE")
            val user = row.opt("USER_ID")
            if (!valid(pkg) || user !is Number || user.toLong() < 0 || user.toLong() > Int.MAX_VALUE ||
                user.toDouble() != user.toLong().toDouble()) throw IOException("Invalid Vector scope")
            entries += Scope(pkg, user.toInt())
        }
        if (entries.distinct().size != entries.size) throw IOException("Duplicate Vector scope")
        return entries.sortedWith(compareBy({ it.packageName }, { it.userId }))
    }

    /** For scope additions, only explicitly installed non-system apps for primary user. */
    @Suppress("DEPRECATION")
    private fun safeTarget(context: Context, target: String) {
        require(valid(target)) { "Invalid target package" }
        val app = try {
            context.packageManager.getApplicationInfo(target, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            throw IllegalArgumentException("Target application is not installed")
        }
        if ((app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0 ||
            target == context.packageName || target.startsWith("com.dergoogler.mmrl") ||
            target == "org.matrix.vector.manager") {
            throw IllegalArgumentException("System, manager, and MMRL targets are excluded from Phase 06 scope writes")
        }
    }

    /**
     * One explicitly confirmed mutation. Re-read immediately before and after; if a write
     * fails after reaching the daemon, report verification required rather than claiming success.
     * No global transaction can be guaranteed against concurrent manager clients.
     */
    fun change(context: Context, request: Change): Result {
        require(valid(request.modulePackage))
        val pre = inspect(context)
        val before = pre.modules.singleOrNull { it.packageName == request.modulePackage }
            ?: throw IllegalArgumentException("Module missing from Vector inventory")
        if (before.enabled != request.expectedEnabled) {
            throw IllegalStateException("Module configuration changed; refresh before editing")
        }
        val originalScope = readScope(request.modulePackage)
        if (request.expectedScope != null && originalScope != request.expectedScope) {
            throw IllegalStateException("Scope changed; refresh before editing")
        }
        val target = request.targetPackage
        when (request.action) {
            Action.ENABLE, Action.DISABLE -> {
                require(target == null) { "Unexpected target for module state change" }
                val wanted = request.action == Action.ENABLE
                if (before.enabled == wanted) throw IllegalArgumentException("Module already in requested state")
                val data = cli("modules", if (wanted) "enable" else "disable", request.modulePackage)
                    .optJSONObject("data") ?: throw IOException("Missing Vector mutation acknowledgement")
                val passed = data.optJSONArray(if (wanted) "Enabled" else "Disabled")
                    ?: throw IOException("Missing module change acknowledgement")
                if (passed.length() != 1 || passed.optString(0) != request.modulePackage ||
                    (data.optJSONArray("Failed")?.length() ?: -1) != 0) {
                    throw IOException("Vector did not confirm a single successful module change")
                }
            }
            Action.ADD_SCOPE, Action.REMOVE_SCOPE -> {
                val pkg = target ?: throw IllegalArgumentException("Target package required")
                safeTarget(context.applicationContext, pkg)
                // user 0 only; edits don't overwrite or rewrite unrelated entries.
                val contains = originalScope.contains(Scope(pkg, 0))
                if (contains == (request.action == Action.ADD_SCOPE)) {
                    throw IllegalArgumentException("Target scope already in requested state")
                }
                cli("scope", if (request.action == Action.ADD_SCOPE) "add" else "rm",
                    request.modulePackage, "$pkg/0")
            }
        }
        val after = inspect(context)
        val afterModule = after.modules.singleOrNull { it.packageName == request.modulePackage }
            ?: throw IOException("Module missing after operation; verify manually")
        val newScope = readScope(request.modulePackage)
        val validResult = when (request.action) {
            Action.ENABLE -> afterModule.enabled && newScope == originalScope
            Action.DISABLE -> !afterModule.enabled && newScope == originalScope
            Action.ADD_SCOPE -> afterModule.enabled == before.enabled &&
                newScope == (originalScope + Scope(target!!, 0)).sortedWith(compareBy({ it.packageName }, { it.userId }))
            Action.REMOVE_SCOPE -> afterModule.enabled == before.enabled &&
                newScope == originalScope.filterNot { it == Scope(target!!, 0) }
        }
        if (!validResult) throw IOException("Vector read-back differs; REFRESH AND VERIFY BEFORE ANY FURTHER CHANGES")
        return Result(after, newScope, "Saved Vector configuration verified. Target process hooks not verified.")
    }
}

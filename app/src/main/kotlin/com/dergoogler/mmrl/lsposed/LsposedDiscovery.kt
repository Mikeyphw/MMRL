package com.dergoogler.mmrl.lsposed

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.dergoogler.mmrl.platform.PlatformManager
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Discovery-only snapshots. This phase never controls Zygisk, LSPosed, Vector or scope state.
 * Installed APK data does not establish framework activation or scope membership.
 */
object LsposedDiscovery {
    data class XposedApk(
        val packageName: String,
        val label: String,
        val format: String,
    )

    data class InstalledManager(
        val packageName: String,
        val label: String,
    )

    data class ApkSnapshot(
        val modules: List<XposedApk>,
        val managers: List<InstalledManager>,
        val examined: Int,
    )

    data class FrameworkModule(
        val id: String,
        val name: String,
        val version: String,
        val state: String,
        val family: String,
    )

    private val managerPackages = setOf(
        "org.lsposed.manager",
        "org.matrix.vector.manager",
        "io.github.libxposed.manager",
    )

    private val modernEntries = listOf(
        "META-INF/xposed/java_init.list",
        "META-INF/xposed/native_init.list",
    )

    /** PackageManager calls and APK scans MUST run on a worker dispatcher. */
    @Suppress("DEPRECATION")
    fun scanInstalledApks(context: Context): ApkSnapshot {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val modules = ArrayList<XposedApk>()
        val managers = ArrayList<InstalledManager>()
        for (app in installed) {
            val packageName = app.packageName
            val label = runCatching { pm.getApplicationLabel(app).toString() }
                .getOrDefault(packageName)
            if (packageName in managerPackages) {
                managers += InstalledManager(packageName, label)
            }

            val legacy = hasLegacyMetadata(app)
            val entry = detectApkEntry(app)
            if (legacy || entry != null) {
                val format = when {
                    entry == "modern" && legacy -> "Modern + legacy"
                    entry == "modern" -> "Modern libxposed"
                    else -> "Legacy Xposed"
                }
                modules += XposedApk(packageName, label, format)
            }
        }
        return ApkSnapshot(
            modules = modules.sortedBy { it.label.lowercase(Locale.ROOT) },
            managers = managers.sortedBy { it.packageName },
            examined = installed.size,
        )
    }

    private fun hasLegacyMetadata(app: ApplicationInfo): Boolean {
        val value = app.metaData?.get("xposedmodule")
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> false
        }
    }

    private fun detectApkEntry(app: ApplicationInfo): String? {
        // Installed base APK only; split-only entries may be missed. Never load code.
        val source = app.sourceDir ?: return null
        return runCatching {
            ZipFile(source).use { zip ->
                when {
                    modernEntries.any { zip.getEntry(it) != null } -> "modern"
                    zip.getEntry("assets/xposed_init") != null -> "legacy"
                    else -> null
                }
            }
        }.getOrNull()
    }

    /**
     * Read installed module state using MMRL's *existing* provider (no new root prompt).
     * Called only from a user-initiated action. A listed module may not be active at boot.
     */
    fun inspectFrameworkModules(): List<FrameworkModule> {
        check(PlatformManager.isAlive) { "MMRL module provider unavailable" }
        return PlatformManager.moduleManager.modules.mapNotNull { module ->
            val family = classifyFramework(module.id.id, module.name) ?: return@mapNotNull null
            FrameworkModule(
                id = module.id.id,
                name = module.name,
                version = module.version,
                state = module.state.name,
                family = family,
            )
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    /** The name/ID is evidence of a module entry, NOT proof that its hooks are active. */
    fun classifyFramework(id: String, name: String): String? {
        val key = id.lowercase(Locale.ROOT)
        val title = name.lowercase(Locale.ROOT)
        return when {
            "vector" in key || "vector" in title -> "Vector"
            key in setOf("zygisk_lsposed", "riru_lsposed", "lsposed") ||
                "lsposed" in key || "lsposed" in title -> "LSPosed"
            else -> null
        }
    }
}

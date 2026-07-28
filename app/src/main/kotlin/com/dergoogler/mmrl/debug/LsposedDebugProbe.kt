package com.dergoogler.mmrl.debug

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.dergoogler.mmrl.lsposed.LsposedRepository
import java.io.File

class LsposedDebugProbe(
    private val context: Context,
    private val activeModuleRoot: File = File("/data/adb/modules"),
    private val stagedModuleRoot: File = File("/data/adb/modules_update"),
) {
    fun managerProbe(): DebugProbeResult {
        val rows = inspectManagerPackages()
        val installed = rows.filter { it.installed }
        val launchable = installed.filter { it.launchable || it.categoryLaunchResolved }
        return DebugProbeResult(
            id = "lsposed-manager-packages",
            title = "LSPosed / Vector manager packages",
            group = DebugProbeGroup.LSPOSED,
            status = when {
                launchable.isNotEmpty() -> DebugProbeStatus.PASS
                installed.isNotEmpty() -> DebugProbeStatus.WARN
                else -> DebugProbeStatus.FAIL
            },
            summary = when {
                launchable.isNotEmpty() -> "${launchable.size} manager package(s) are installed and launchable."
                installed.isNotEmpty() -> "Manager package is installed but MMRL could not resolve a launch intent."
                else -> "No known LSPosed/libxposed/Vector manager package is visible to MMRL."
            },
            evidence = rows.flatMap { row ->
                listOf(
                    DebugEvidence(row.packageName, "installed=${row.installed}, version=${row.versionName.ifBlank { "unknown" }}"),
                    DebugEvidence("${row.packageName} launch", "launcher=${row.launchable}, category=${row.categoryLaunchResolved}, activity=${row.resolvedActivity.ifBlank { "none" }}"),
                )
            },
            remedies = when {
                installed.isEmpty() -> listOf("Install LSPosed, libxposed, or Vector Manager; then reopen this probe.")
                launchable.isEmpty() -> listOf("The package is visible, but its launcher/category intent changed. Share this debug report so MMRL can add the new action/category.")
                else -> emptyList()
            },
        )
    }

    fun providerProbe(): DebugProbeResult {
        val modules = inspectProviderModules()
        val active = modules.filter { it.active }
        val staged = modules.filterNot { it.active }
        return DebugProbeResult(
            id = "lsposed-provider-modules",
            title = "LSPosed / Vector provider modules",
            group = DebugProbeGroup.LSPOSED,
            status = when {
                active.any { it.looksUsable } -> DebugProbeStatus.PASS
                active.isNotEmpty() || staged.isNotEmpty() -> DebugProbeStatus.WARN
                else -> DebugProbeStatus.FAIL
            },
            summary = when {
                active.any { it.looksUsable } -> "Active provider module found."
                active.isNotEmpty() -> "Active provider folder exists, but expected provider files are incomplete."
                staged.isNotEmpty() -> "Only staged provider module found; reboot may be required."
                else -> "No known LSPosed or Vector provider module was found."
            },
            evidence = if (modules.isEmpty()) {
                listOf(
                    DebugEvidence("active root", activeModuleRoot.absolutePath),
                    DebugEvidence("staged root", stagedModuleRoot.absolutePath),
                    DebugEvidence("known ids", LsposedRepository.PROVIDER_MODULE_IDS.joinToString()),
                )
            } else {
                modules.flatMap { module -> module.toEvidence() }
            },
            remedies = if (modules.isEmpty()) {
                listOf("Check whether the module is installed under /data/adb/modules or /data/adb/modules_update.")
            } else {
                emptyList()
            },
        )
    }

    private fun inspectManagerPackages(): List<ManagerPackageRow> {
        val pm = context.packageManager
        return LsposedRepository.LSPOSED_MANAGER_PACKAGES.map { packageName ->
            val info = packageInfo(pm, packageName)
            val launcher = runCatching { pm.getLaunchIntentForPackage(packageName) }.getOrNull()
            val categoryIntent = managerCategoryLaunchIntent(packageName)
            val categoryResolve = categoryIntent?.let { resolveActivity(pm, it) }
            ManagerPackageRow(
                packageName = packageName,
                installed = info != null,
                versionName = info?.versionName.orEmpty(),
                launchable = launcher != null,
                categoryLaunchResolved = categoryResolve != null,
                resolvedActivity = categoryResolve?.activityInfo?.name.orEmpty(),
            )
        }
    }

    private fun managerCategoryLaunchIntent(packageName: String): Intent? = when (packageName) {
        VECTOR_MANAGER_PACKAGE -> Intent(Intent.ACTION_MAIN)
            .setPackage(packageName)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addCategory("$packageName.LAUNCH_MANAGER")
        else -> null
    }

    private fun inspectProviderModules(): List<ProviderModuleRow> = listOf(
        activeModuleRoot to true,
        stagedModuleRoot to false,
    ).flatMap { (root, active) -> inspectProviderRoot(root, active) }

    private fun inspectProviderRoot(root: File, active: Boolean): List<ProviderModuleRow> {
        if (!root.isDirectory) return emptyList()
        val preferred = LsposedRepository.PROVIDER_MODULE_IDS.map { File(root, it) }
        val discovered = runCatching { root.listFiles().orEmpty().filter(File::isDirectory) }.getOrDefault(emptyList())
        return (preferred + discovered)
            .distinctBy { it.absolutePath }
            .mapNotNull { directory -> providerRow(directory, active) }
            .sortedWith(compareByDescending<ProviderModuleRow> { it.active }.thenBy { it.folder })
    }

    private fun providerRow(directory: File, active: Boolean): ProviderModuleRow? {
        if (!directory.isDirectory) return null
        val properties = readModuleProperties(directory.resolve(MODULE_PROP))
        val id = properties["id"].orEmpty().ifBlank { directory.name }
        val identity = listOf(id, directory.name, properties["name"].orEmpty(), properties["description"].orEmpty())
            .joinToString(" ")
            .lowercase()
        val hasProviderFile = directory.resolve("manager.apk").isFile ||
            directory.resolve("daemon.apk").isFile ||
            directory.resolve("framework/lspd.dex").isFile
        val knownId = LsposedRepository.PROVIDER_MODULE_IDS.any { known ->
            known.equals(id, ignoreCase = true) || known.equals(directory.name, ignoreCase = true)
        }
        val nameLooksLikeProvider = ("lsposed" in identity || "vector" in identity || "xposed-compatible" in identity) && hasProviderFile
        if (!knownId && !nameLooksLikeProvider) return null
        return ProviderModuleRow(
            active = active,
            folder = directory.name,
            moduleId = id,
            name = properties["name"].orEmpty().ifBlank { id },
            version = properties["version"].orEmpty(),
            disabled = directory.resolve("disable").isFile,
            removalPending = directory.resolve("remove").isFile,
            actionSh = directory.resolve("action.sh").isFile,
            managerApk = directory.resolve("manager.apk").isFile,
            daemonApk = directory.resolve("daemon.apk").isFile,
            lspdDex = directory.resolve("framework/lspd.dex").isFile,
        )
    }

    private fun readModuleProperties(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            file.useLines { lines ->
                lines.map(String::trim)
                    .filter { line -> line.isNotEmpty() && !line.startsWith('#') && '=' in line }
                    .associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
            }
        }.getOrDefault(emptyMap())
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(pm: PackageManager, packageName: String): PackageInfo? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            pm.getPackageInfo(packageName, 0)
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun resolveActivity(pm: PackageManager, intent: Intent): ResolveInfo? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }.getOrNull()

    private data class ManagerPackageRow(
        val packageName: String,
        val installed: Boolean,
        val versionName: String,
        val launchable: Boolean,
        val categoryLaunchResolved: Boolean,
        val resolvedActivity: String,
    )

    private data class ProviderModuleRow(
        val active: Boolean,
        val folder: String,
        val moduleId: String,
        val name: String,
        val version: String,
        val disabled: Boolean,
        val removalPending: Boolean,
        val actionSh: Boolean,
        val managerApk: Boolean,
        val daemonApk: Boolean,
        val lspdDex: Boolean,
    ) {
        val looksUsable: Boolean get() = !disabled && (actionSh || managerApk || daemonApk || lspdDex)

        fun toEvidence(): List<DebugEvidence> = listOf(
            DebugEvidence("${if (active) "active" else "staged"}:$folder", "id=$moduleId, name=$name, version=${version.ifBlank { "unknown" }}"),
            DebugEvidence("$folder files", "action.sh=$actionSh, manager.apk=$managerApk, daemon.apk=$daemonApk, framework/lspd.dex=$lspdDex"),
            DebugEvidence("$folder markers", "disabled=$disabled, remove=$removalPending"),
        )
    }

    private companion object {
        const val VECTOR_MANAGER_PACKAGE = "org.matrix.vector.manager"
        const val MODULE_PROP = "module.prop"
    }
}

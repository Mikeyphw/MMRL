package com.dergoogler.mmrl.debug

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.dergoogler.mmrl.lsposed.LsposedRepository
import com.dergoogler.mmrl.platform.file.SuFile
import com.dergoogler.mmrl.platform.file.useLines
import java.io.File

class LsposedDebugProbe(
    private val context: Context,
    private val activeModuleRoot: File = File("/data/adb/modules"),
    private val stagedModuleRoot: File = File("/data/adb/modules_update"),
) {
    fun managerProbe(): DebugProbeResult {
        val rows = inspectManagerPackages()
        val installed = rows.filter { it.installed }
        val launchable = installed.filter { it.launchable || it.categoryLaunchResolved || it.actionLaunchResolved }
        val visibleMatches = visibleManagerLikePackages(context.packageManager)
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
                installed.isNotEmpty() -> "Manager package is installed but MMRL could not resolve a launcher, category, or action intent."
                else -> "No known LSPosed/libxposed/Vector manager package is visible to MMRL."
            },
            evidence = rows.flatMap { row ->
                listOf(
                    DebugEvidence(row.packageName, "installed=${row.installed}, version=${row.versionName.ifBlank { "unknown" }}"),
                    DebugEvidence(
                        "${row.packageName} launch",
                        "launcher=${row.launchable}, category=${row.categoryLaunchResolved}, action=${row.actionLaunchResolved}, activity=${row.resolvedActivity.ifBlank { "none" }}",
                    ),
                )
            } + listOf(
                DebugEvidence("Vector manager package", VECTOR_MANAGER_PACKAGE),
                DebugEvidence("visible manager-like package scan", "count=${visibleMatches.size}, matches=${visibleMatches.joinToString().ifBlank { "none" }}"),
                DebugEvidence("package visibility policy", "QUERY_ALL_PACKAGES declared; explicit Vector manager/daemon queries declared"),
            ),
            remedies = when {
                installed.isEmpty() -> listOf("If the manager is installed, check whether it is installed for another Android user/profile or hidden from this app profile.")
                launchable.isEmpty() -> listOf("The package is visible, but its launcher/category/action intent changed. Share this debug report so MMRL can add the new action/category.")
                else -> emptyList()
            },
        )
    }

    fun providerProbe(): DebugProbeResult {
        val rootSnapshots = listOf(
            rootSnapshot(rootAccessFile(activeModuleRoot), "active root"),
            rootSnapshot(rootAccessFile(stagedModuleRoot), "staged root"),
        )
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
                else -> "No known LSPosed or Vector provider module was found. Root directory visibility is included below."
            },
            evidence = rootSnapshots.flatMap { it.toEvidence() } +
                listOf(DebugEvidence("known ids", LsposedRepository.PROVIDER_MODULE_IDS.joinToString())) +
                if (modules.isEmpty()) emptyList() else modules.flatMap { module -> module.toEvidence() },
            remedies = if (modules.isEmpty()) {
                listOf("Check root directory visibility, discovered folder names, and module.prop readability in this report.")
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
            val categoryResolve = resolveFirstActivity(pm, categoryIntent)
            val actionIntent = managerActionLaunchIntent(packageName)
            val actionResolve = resolveFirstActivity(pm, actionIntent)
            ManagerPackageRow(
                packageName = packageName,
                installed = info != null,
                versionName = info?.versionName.orEmpty(),
                launchable = launcher != null,
                categoryLaunchResolved = categoryResolve != null,
                actionLaunchResolved = actionResolve != null,
                resolvedActivity = listOfNotNull(
                    categoryResolve?.activityInfo?.name,
                    actionResolve?.activityInfo?.name,
                ).firstOrNull().orEmpty(),
            )
        }
    }

    private fun managerCategoryLaunchIntent(packageName: String): Intent = Intent(Intent.ACTION_MAIN)
        .setPackage(packageName)
        .addCategory(Intent.CATEGORY_DEFAULT)
        .addCategory("$packageName.LAUNCH_MANAGER")

    private fun managerActionLaunchIntent(packageName: String): Intent = Intent("$packageName.LAUNCH_MANAGER")
        .setPackage(packageName)
        .addCategory(Intent.CATEGORY_DEFAULT)

    private fun inspectProviderModules(): List<ProviderModuleRow> = listOf(
        activeModuleRoot to true,
        stagedModuleRoot to false,
    ).flatMap { (root, active) -> inspectProviderRoot(root, active) }

    private fun inspectProviderRoot(root: File, active: Boolean): List<ProviderModuleRow> {
        val rootFile = rootAccessFile(root)
        if (!safeIsDirectory(rootFile)) return emptyList()
        val preferred = LsposedRepository.PROVIDER_MODULE_IDS.map { id -> suChild(rootFile, id) }
        val discovered = listRootNames(rootFile).names
            .map { name -> suChild(rootFile, name) }
            .filter(::safeIsDirectory)
        return (preferred + discovered)
            .distinctBy { it.absolutePath }
            .mapNotNull { directory -> providerRow(directory, active) }
            .sortedWith(compareByDescending<ProviderModuleRow> { it.active }.thenBy { it.folder })
    }

    private fun providerRow(directory: File, active: Boolean): ProviderModuleRow? {
        if (!safeIsDirectory(directory)) return null
        val propFile = suChild(directory, MODULE_PROP)
        val properties = readModuleProperties(propFile)
        val id = properties["id"].orEmpty().ifBlank { directory.name }
        val identity = listOf(id, directory.name, properties["name"].orEmpty(), properties["description"].orEmpty())
            .joinToString(" ")
            .lowercase()
        val managerApkFile = suChild(directory, "manager.apk")
        val daemonApkFile = suChild(directory, "daemon.apk")
        val lspdDexFile = suChild(directory, "framework/lspd.dex")
        val hasProviderFile = safeIsFile(managerApkFile) || safeIsFile(daemonApkFile) || safeIsFile(lspdDexFile)
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
            modulePropReadable = safeIsFile(propFile) && properties.isNotEmpty(),
            disabled = safeIsFile(suChild(directory, "disable")),
            removalPending = safeIsFile(suChild(directory, "remove")),
            actionSh = safeIsFile(suChild(directory, "action.sh")),
            managerApk = safeIsFile(managerApkFile),
            daemonApk = safeIsFile(daemonApkFile),
            lspdDex = safeIsFile(lspdDexFile),
        )
    }

    private fun visibleManagerLikePackages(pm: PackageManager): List<String> = runCatching {
        installedPackages(pm)
            .map { it.packageName }
            .filter { name ->
                val lower = name.lowercase()
                "lsposed" in lower || "libxposed" in lower || "vector" in lower || "matrix" in lower
            }
            .sorted()
            .take(40)
    }.getOrElse { error -> listOf("error=${error.message ?: error::class.java.simpleName}") }

    private fun rootSnapshot(root: File, label: String): ModuleRootSnapshot {
        val listing = listRootNames(root)
        return ModuleRootSnapshot(
            label = label,
            path = root.absolutePath,
            exists = safeExists(root),
            directory = safeIsDirectory(root),
            readable = safeCanRead(root),
            childCount = listing.names.size,
            childrenPreview = listing.names.sorted().take(ROOT_PREVIEW_LIMIT),
            listingError = listing.error.orEmpty(),
        )
    }

    private fun listRootNames(root: File): RootListing = runCatching {
        RootListing(root.list()?.toList().orEmpty(), null)
    }.getOrElse { error -> RootListing(emptyList(), error.message ?: error::class.java.simpleName) }

    private fun readModuleProperties(file: File): Map<String, String> {
        if (!safeIsFile(file)) return emptyMap()
        return runCatching {
            val readLines: ((Sequence<String>) -> Map<String, String>) = { lines ->
                lines.map(String::trim)
                    .filter { line -> line.isNotEmpty() && !line.startsWith('#') && '=' in line }
                    .associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
            }
            if (file is SuFile) {
                file.useLines(block = readLines)
            } else {
                file.useLines(block = readLines)
            }
        }.getOrDefault(emptyMap())
    }

    private fun rootAccessFile(root: File): File = if (root.absolutePath.startsWith(DATA_ADB_ROOT)) {
        SuFile(root.absolutePath)
    } else {
        root
    }

    private fun suChild(parent: File, child: String): File = if (parent is SuFile || parent.absolutePath.startsWith(DATA_ADB_ROOT)) {
        SuFile(parent.absolutePath.trimEnd('/') + "/" + child)
    } else {
        File(parent, child)
    }

    private fun safeExists(file: File): Boolean = runCatching { file.exists() }.getOrDefault(false)

    private fun safeIsDirectory(file: File): Boolean = runCatching { file.isDirectory }.getOrDefault(false)

    private fun safeIsFile(file: File): Boolean = runCatching { file.isFile }.getOrDefault(false)

    private fun safeCanRead(file: File): Boolean = runCatching { file.canRead() }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun packageInfo(pm: PackageManager, packageName: String): PackageInfo? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            pm.getPackageInfo(packageName, 0)
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun resolveFirstActivity(pm: PackageManager, intent: Intent): ResolveInfo? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun installedPackages(pm: PackageManager): List<PackageInfo> = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            pm.getInstalledPackages(0)
        }
    }.getOrDefault(emptyList())

    private data class ManagerPackageRow(
        val packageName: String,
        val installed: Boolean,
        val versionName: String,
        val launchable: Boolean,
        val categoryLaunchResolved: Boolean,
        val actionLaunchResolved: Boolean,
        val resolvedActivity: String,
    )

    private data class ProviderModuleRow(
        val active: Boolean,
        val folder: String,
        val moduleId: String,
        val name: String,
        val version: String,
        val modulePropReadable: Boolean,
        val disabled: Boolean,
        val removalPending: Boolean,
        val actionSh: Boolean,
        val managerApk: Boolean,
        val daemonApk: Boolean,
        val lspdDex: Boolean,
    ) {
        val looksUsable: Boolean get() = !disabled && (actionSh || managerApk || daemonApk || lspdDex)

        fun toEvidence(): List<DebugEvidence> = listOf(
            DebugEvidence("${if (active) "active" else "staged"}:$folder", "id=$moduleId, name=$name, version=${version.ifBlank { "unknown" }}, module.prop=$modulePropReadable"),
            DebugEvidence("$folder files", "action.sh=$actionSh, manager.apk=$managerApk, daemon.apk=$daemonApk, framework/lspd.dex=$lspdDex"),
            DebugEvidence("$folder markers", "disabled=$disabled, remove=$removalPending"),
        )
    }

    private data class RootListing(
        val names: List<String>,
        val error: String?,
    )

    private data class ModuleRootSnapshot(
        val label: String,
        val path: String,
        val exists: Boolean,
        val directory: Boolean,
        val readable: Boolean,
        val childCount: Int,
        val childrenPreview: List<String>,
        val listingError: String,
    ) {
        fun toEvidence(): List<DebugEvidence> = listOf(
            DebugEvidence(label, "path=$path, exists=$exists, directory=$directory, readable=$readable, children=$childCount"),
            DebugEvidence("$label preview", childrenPreview.joinToString().ifBlank { "none" }),
            DebugEvidence("$label list error", listingError.ifBlank { "none" }),
        )
    }

    private companion object {
        const val VECTOR_MANAGER_PACKAGE = "org.matrix.vector.manager"
        const val MODULE_PROP = "module.prop"
        const val ROOT_PREVIEW_LIMIT = 30
        const val DATA_ADB_ROOT = "/data/adb/"
    }
}

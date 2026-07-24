package com.dergoogler.mmrl.lsposed

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.dergoogler.mmrl.app.moshi
import com.dergoogler.mmrl.network.NetworkUtils
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

class LsposedRepository(private val context: Context) {
    private val client by lazy { NetworkUtils.createOkHttpClient() }
    private val moduleListAdapter by lazy {
        moshi.adapter<List<LsposedRepoModule>>(
            Types.newParameterizedType(List::class.java, LsposedRepoModule::class.java),
        )
    }
    private val moduleAdapter by lazy { moshi.adapter(LsposedRepoModule::class.java) }
    private val cacheDir by lazy { File(context.cacheDir, "lsposed-repo").apply { mkdirs() } }

    suspend fun loadModules(forceRefresh: Boolean = false): List<LsposedRepoModule> = withContext(Dispatchers.IO) {
        val cache = File(cacheDir, "modules.json")
        val body =
            if (!forceRefresh && cache.isFile && cache.length() > 0L) {
                cache.readText()
            } else {
                requestText("https://modules.lsposed.org/modules.json").also(cache::writeText)
            }
        moduleListAdapter.fromJson(body).orEmpty().filterNot { it.hide == true }.sortedBy { it.displayName.lowercase() }
    }

    suspend fun loadDetail(packageName: String): LsposedRepoModule = withContext(Dispatchers.IO) {
        moduleAdapter.fromJson(requestText("https://modules.lsposed.org/module/$packageName.json"))
            ?: error("LSPosed repository returned empty details for $packageName")
    }

    suspend fun prepareApk(module: LsposedRepoModule): PreparedApk = withContext(Dispatchers.IO) {
        val detailed = if (LsposedModulePolicy.bestInstallAsset(module) == null) module.withDetail(loadDetail(module.packageName)) else module
        val (_, asset) = LsposedModulePolicy.bestInstallAsset(detailed)
            ?: error("No APK asset found for ${module.displayName}. Open the source page and install the APK manually.")
        val url = asset.downloadUrl ?: error("APK asset does not include a download URL")
        val safeName = asset.name?.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "${module.packageName}-${System.currentTimeMillis()}.apk"
        val out = File(cacheDir, safeName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Unable to download APK: HTTP ${response.code}")
            }
            val body = response.body ?: error("Unable to download APK: empty response")
            out.outputStream().use { sink -> body.byteStream().copyTo(sink) }
        }
        require(out.length() > 0L) { "Downloaded APK is empty" }
        PreparedApk(
            module = detailed,
            asset = asset,
            uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", out),
        )
    }

    fun installedModules(index: List<LsposedRepoModule>): List<LsposedInstalledModule> {
        val pm = context.packageManager
        val repoByPackage = index.associateBy { it.packageName }
        val packages = installedPackages(pm)
        return packages.mapNotNull { info ->
            val packageName = info.packageName
            val appInfo = info.applicationInfo
            val repoModule = repoByPackage[packageName]
            val isXposed = appInfo?.metaData?.let { meta ->
                meta.getBoolean("xposedmodule", false) ||
                    meta.containsKey("xposedminversion") ||
                    meta.containsKey("xposeddescription")
            } == true
            if (repoModule == null && !isXposed) return@mapNotNull null
            LsposedInstalledModule(
                packageName = packageName,
                label = appInfo?.loadLabel(pm)?.toString().orEmpty().ifBlank { packageName },
                installedVersionName = info.versionName,
                installedVersionCode = PackageInfoCompat.getLongVersionCode(info),
                repoModule = repoModule,
                launchable = pm.getLaunchIntentForPackage(packageName) != null,
                detectedByXposedMetadata = isXposed,
            )
        }.sortedWith(compareByDescending<LsposedInstalledModule> { it.hasUpdate }.thenBy { it.displayName.lowercase() })
    }

    fun providerStatus(): LsposedProviderStatus {
        val managerInstalled = lsposedManagerIntent() != null
        val active = findProviderCandidate(PROVIDER_ACTIVE_ROOT, active = true)
        val staged = findProviderCandidate(PROVIDER_UPDATE_ROOT, active = false)
        val selected = active ?: staged
        return LsposedProviderStatus(
            installed = selected != null,
            moduleId = selected?.moduleId,
            folder = selected?.folder,
            name = selected?.name,
            version = selected?.version,
            active = active != null,
            updatePending = staged != null,
            disabled = active?.disabled == true,
            actionAvailable = active?.actionAvailable == true,
            managerApkPresent = selected?.managerApkPresent == true,
            managerPackageInstalled = managerInstalled,
        )
    }

    fun lsposedManagerIntent(): Intent? {
        val pm = context.packageManager
        return LSPOSED_MANAGER_PACKAGES.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
    }

    fun lsposedProviderActionModuleId(): String? = providerStatus()
        .takeIf { it.actionAvailable }
        ?.moduleId
        ?.takeIf { it.isNotBlank() }

    fun launchAppIntent(packageName: String): Intent? = context.packageManager.getLaunchIntentForPackage(packageName)

    private fun findProviderCandidate(root: File, active: Boolean): ProviderCandidate? {
        if (!root.isDirectory) return null

        PROVIDER_MODULE_IDS.asSequence()
            .map { id -> File(root, id) }
            .mapNotNull { directory -> providerCandidateFromDirectory(directory, active) }
            .firstOrNull()
            ?.let { return it }

        return runCatching { root.listFiles().orEmpty().toList() }
            .getOrDefault(emptyList())
            .asSequence()
            .filter(File::isDirectory)
            .sortedBy(File::getAbsolutePath)
            .mapNotNull { directory -> providerCandidateFromDirectory(directory, active) }
            .firstOrNull()
    }

    private fun providerCandidateFromDirectory(directory: File, active: Boolean): ProviderCandidate? {
        if (!directory.isDirectory) return null
        val properties = readModuleProperties(File(directory, MODULE_PROP))
        val id = properties["id"].orEmpty().ifBlank { directory.name }
        val name = properties["name"].orEmpty()
        val description = properties["description"].orEmpty()
        val identity = listOf(id, directory.name, name, description)
            .joinToString(" ")
            .lowercase()
        val hasProviderFiles = File(directory, "manager.apk").isFile ||
            File(directory, "framework/lspd.dex").isFile ||
            File(directory, "daemon.apk").isFile
        val knownProvider = PROVIDER_MODULE_IDS.any { candidate ->
            id.equals(candidate, ignoreCase = true) || directory.name.equals(candidate, ignoreCase = true)
        }
        val nameLooksLikeProvider = ("lsposed" in identity || "xposed-compatible" in identity || "vector" in identity) && hasProviderFiles
        if (!knownProvider && !nameLooksLikeProvider) return null

        return ProviderCandidate(
            moduleId = id,
            folder = directory.name,
            name = name.ifBlank { id },
            version = properties["version"].orEmpty(),
            disabled = File(directory, "disable").isFile,
            actionAvailable = active && File(directory, "action.sh").isFile,
            managerApkPresent = File(directory, "manager.apk").isFile,
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

    private fun requestText(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Unable to load LSPosed repository: HTTP ${response.code}")
            }
            return response.body?.string() ?: error("Unable to load LSPosed repository: empty response")
        }
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(pm: PackageManager): List<PackageInfo> =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
        }

    data class PreparedApk(
        val module: LsposedRepoModule,
        val asset: LsposedReleaseAsset,
        val uri: Uri,
    )

    private data class ProviderCandidate(
        val moduleId: String,
        val folder: String,
        val name: String,
        val version: String,
        val disabled: Boolean,
        val actionAvailable: Boolean,
        val managerApkPresent: Boolean,
    )

    companion object {
        val LSPOSED_MANAGER_PACKAGES = listOf(
            "org.lsposed.manager",
            "io.github.libxposed.manager",
            "org.lsposed.lspd",
        )

        val PROVIDER_MODULE_IDS = listOf(
            "zygisk_vector",
            "zygisk_lsposed",
            "riru_lsposed",
            "lsposed",
        )

        private val PROVIDER_ACTIVE_ROOT = File("/data/adb/modules")
        private val PROVIDER_UPDATE_ROOT = File("/data/adb/modules_update")
        private const val MODULE_PROP = "module.prop"

        fun packageInstallerIntent(uri: Uri): Intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

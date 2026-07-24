package com.dergoogler.mmrl.lsposed

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.dergoogler.mmrl.network.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LsposedRepository(private val context: Context) {
    private val client by lazy { NetworkUtils.createOkHttpClient() }
    private val cacheDir by lazy { File(context.cacheDir, "lsposed-repo").apply { mkdirs() } }

    suspend fun loadModules(forceRefresh: Boolean = false): List<LsposedRepoModule> = withContext(Dispatchers.IO) {
        val cache = File(cacheDir, "modules.json")
        val body =
            if (!forceRefresh && cache.isFile && cache.length() > 0L) {
                cache.readText()
            } else {
                requestText("https://modules.lsposed.org/modules.json").also(cache::writeText)
            }
        parseModules(body).filterNot { it.hide == true }.sortedBy { it.displayName.lowercase() }
    }

    suspend fun loadDetail(packageName: String): LsposedRepoModule = withContext(Dispatchers.IO) {
        parseModule(JSONObject(requestText("https://modules.lsposed.org/module/$packageName.json")))
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
            val packageName = info.packageName ?: return@mapNotNull null
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

    fun lsposedManagerIntent(): Intent? {
        val pm = context.packageManager
        return LSPOSED_MANAGER_PACKAGES.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
    }

    fun launchAppIntent(packageName: String): Intent? = context.packageManager.getLaunchIntentForPackage(packageName)

    private fun requestText(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Unable to load LSPosed repository: HTTP ${response.code}")
            }
            return response.body?.string() ?: error("Unable to load LSPosed repository: empty response")
        }
    }

    private fun parseModules(body: String): List<LsposedRepoModule> {
        val array = JSONArray(body)
        return List(array.length()) { index -> parseModule(array.optJSONObject(index)) }.filterNotNull()
    }

    private fun parseModule(json: JSONObject?): LsposedRepoModule? {
        if (json == null) return null
        val name = json.stringOrNull("name") ?: return null
        return LsposedRepoModule(
            name = name,
            description = json.stringOrNull("description"),
            url = json.stringOrNull("url"),
            homepageUrl = json.stringOrNull("homepageUrl"),
            latestRelease = json.stringOrNull("latestRelease"),
            latestReleaseTime = json.stringOrNull("latestReleaseTime"),
            latestBetaRelease = json.stringOrNull("latestBetaRelease"),
            latestBetaReleaseTime = json.stringOrNull("latestBetaReleaseTime"),
            latestSnapshotRelease = json.stringOrNull("latestSnapshotRelease"),
            latestSnapshotReleaseTime = json.stringOrNull("latestSnapshotReleaseTime"),
            releases = json.releaseList("releases"),
            betaReleases = json.releaseList("betaReleases"),
            snapshotReleases = json.releaseList("snapshotReleases"),
            readme = json.stringOrNull("readme"),
            summary = json.stringOrNull("summary"),
            scope = json.stringList("scope"),
            sourceUrl = json.stringOrNull("sourceUrl"),
            hide = if (json.has("hide")) json.optBoolean("hide") else null,
            additionalAuthors = json.stringList("additionalAuthors").takeIf { it.isNotEmpty() },
            updatedAt = json.stringOrNull("updatedAt"),
            createdAt = json.stringOrNull("createdAt"),
            stargazerCount = if (json.has("stargazerCount")) json.optInt("stargazerCount") else null,
        )
    }

    private fun JSONObject.releaseList(name: String): List<LsposedRelease> {
        val array = optJSONArray(name) ?: return emptyList()
        return List(array.length()) { index ->
            val json = array.optJSONObject(index)
            if (json == null) {
                null
            } else {
                LsposedRelease(
                    name = json.stringOrNull("name"),
                    url = json.stringOrNull("url"),
                    description = json.stringOrNull("description"),
                    descriptionHTML = json.stringOrNull("descriptionHTML"),
                    createdAt = json.stringOrNull("createdAt"),
                    publishedAt = json.stringOrNull("publishedAt"),
                    updatedAt = json.stringOrNull("updatedAt"),
                    tagName = json.stringOrNull("tagName"),
                    isPrerelease = if (json.has("isPrerelease")) json.optBoolean("isPrerelease") else null,
                    releaseAssets = json.assetList("releaseAssets"),
                )
            }
        }.filterNotNull()
    }

    private fun JSONObject.assetList(name: String): List<LsposedReleaseAsset> {
        val array = optJSONArray(name) ?: return emptyList()
        return List(array.length()) { index ->
            val json = array.optJSONObject(index)
            if (json == null) {
                null
            } else {
                LsposedReleaseAsset(
                    name = json.stringOrNull("name"),
                    contentType = json.stringOrNull("contentType"),
                    downloadUrl = json.stringOrNull("downloadUrl"),
                    downloadCount = json.optInt("downloadCount", 0),
                    size = json.optInt("size", 0),
                )
            }
        }.filterNotNull()
    }

    private fun JSONObject.stringList(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return List(array.length()) { index -> array.optString(index).takeIf { it.isNotBlank() } }.filterNotNull()
    }

    private fun JSONObject.stringOrNull(name: String): String? =
        optString(name).takeIf { it.isNotBlank() && it != "null" }

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

    companion object {
        val LSPOSED_MANAGER_PACKAGES = listOf(
            "org.lsposed.manager",
            "io.github.libxposed.manager",
            "org.lsposed.lspd",
        )

        fun packageInstallerIntent(uri: Uri): Intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

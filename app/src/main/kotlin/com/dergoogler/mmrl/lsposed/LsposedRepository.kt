package com.dergoogler.mmrl.lsposed

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.dergoogler.mmrl.app.moshi
import com.dergoogler.mmrl.github.GitHubTokenStore
import com.dergoogler.mmrl.network.NetworkPolicy
import com.dergoogler.mmrl.network.NetworkUtils
import com.dergoogler.mmrl.platform.file.SuFile
import com.dergoogler.mmrl.platform.file.useLines
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.URI
import java.security.MessageDigest

class LsposedRepository(private val context: Context) {
    private val client by lazy { NetworkUtils.createOkHttpClient() }
    private val moduleListAdapter by lazy {
        moshi.adapter<List<LsposedRepoModule>>(
            Types.newParameterizedType(List::class.java, LsposedRepoModule::class.java),
        )
    }
    private val moduleAdapter by lazy { moshi.adapter(LsposedRepoModule::class.java) }
    private val cacheDir by lazy { File(context.cacheDir, "lsposed-repo").apply { mkdirs() } }
    private val scopeRepository by lazy { LsposedScopeRepository(context) }
    private val githubTokenStore by lazy { GitHubTokenStore(context) }

    suspend fun loadModules(forceRefresh: Boolean = false): List<LsposedRepoModule> =
        loadModulesWithState(forceRefresh).modules

    suspend fun loadModulesWithState(forceRefresh: Boolean = false): LsposedRepositoryCacheState = withContext(Dispatchers.IO) {
        val cache = File(cacheDir, "modules.json")
        val meta = File(cacheDir, "modules.meta.json")
        val cachedBody = cache.takeIf { it.isFile && it.length() > 0L }?.readText()
        val cachedModules = cachedBody?.let { body ->
            runCatching { parseRepositoryModules(body) }
                .onFailure { error -> Timber.w(error, "Ignoring invalid LSPosed repository cache") }
                .getOrNull()
        }
        val cachedFetchedAt = readCacheFetchedAt(meta)
        val cachedFreshness = LsposedRepositoryCachePolicy.freshnessFor(cachedFetchedAt)
        val canUseFreshCache = !forceRefresh && cachedBody != null && cachedModules != null && cachedFreshness == LsposedCacheFreshness.FRESH

        val (loaded, modules) = if (canUseFreshCache) {
            CachedRepositoryBody(
                body = cachedBody.orEmpty(),
                fetchedAt = cachedFetchedAt,
                sourceUrl = readCacheSourceUrl(meta),
                freshness = LsposedCacheFreshness.FRESH,
                errorMessage = null,
            ) to cachedModules.orEmpty()
        } else {
            runCatching {
                val remote = requestTextWithSource(LSPOSED_MODULES_URL, LSPOSED_MODULES_FALLBACK_URLS)
                // Validate and normalize the new generation before replacing the last known-good cache.
                val parsed = parseRepositoryModules(remote.body)
                writeAtomic(cache, remote.body)
                writeAtomic(
                    meta,
                    JSONObject()
                        .put("fetched_at", remote.fetchedAt)
                        .put("source_url", remote.sourceUrl)
                        .toString(),
                )
                remote.copy(freshness = LsposedCacheFreshness.FRESH) to parsed
            }.getOrElse { failure ->
                if (cachedBody != null && cachedModules != null) {
                    CachedRepositoryBody(
                        body = cachedBody,
                        fetchedAt = cachedFetchedAt,
                        sourceUrl = readCacheSourceUrl(meta),
                        freshness = LsposedCacheFreshness.STALE,
                        errorMessage = failure.message ?: "Unable to refresh LSPosed repository",
                    ) to cachedModules
                } else {
                    throw failure
                }
            }
        }

        LsposedRepositoryCacheState(
            modules = modules,
            fetchedAt = loaded.fetchedAt,
            sourceUrl = loaded.sourceUrl,
            freshness = loaded.freshness,
            errorMessage = loaded.errorMessage,
        )
    }

    private fun parseRepositoryModules(body: String): List<LsposedRepoModule> =
        LsposedRepositoryIndexPolicy
            .validate(
                moduleListAdapter.fromJson(body)
                    ?: error("LSPosed repository returned an empty JSON payload"),
            )
            .filterNot { it.hide == true }
            .sortedBy { it.displayName.lowercase() }

    suspend fun loadDetail(packageName: String): LsposedRepoModule = withContext(Dispatchers.IO) {
        moduleAdapter.fromJson(
            requestText(
                "https://modules.lsposed.org/module/$packageName.json",
                lsposedModuleFallbackUrls(packageName),
            ),
        )
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
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .applyGitHubAuthentication(url, githubTokenStore.getToken())
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            NetworkPolicy.requireSuccessful(response)
            val body = response.body ?: error("Unable to download APK: empty response")
            val length = body.contentLength()
            require(NetworkPolicy.declaredDownloadLengthAllowed(length)) {
                "APK download exceeds the ${NetworkPolicy.MAX_DOWNLOAD_BYTES} byte safety limit"
            }
            var received = 0L
            body.byteStream().buffered().use { input ->
                out.outputStream().buffered().use { sink ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        received = NetworkPolicy.addReceivedBytes(received, count)
                        sink.write(buffer, 0, count)
                    }
                }
            }
        }
        require(out.length() > 0L) { "Downloaded APK is empty" }
        val identity = verifyDownloadedApk(out, detailed)
        PreparedApk(
            module = detailed,
            asset = asset,
            uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", out),
            identity = identity,
        )
    }

    suspend fun scopeState(): LsposedScopeState = scopeRepository.readState()

    fun installedTargets(): List<LsposedScopeTarget> = scopeRepository.installedTargets()

    suspend fun applyScopePlan(plan: LsposedScopeEditPlan): LsposedScopeState = scopeRepository.applyPlan(plan)

    fun installedModules(
        index: List<LsposedRepoModule>,
        scopeState: LsposedScopeState = LsposedScopeState(),
    ): List<LsposedInstalledModule> {
        val pm = context.packageManager
        val repoByPackage = index.associateBy { it.packageName }
        val scopeByPackage = scopeState.modulesByPackage
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
                scope = scopeByPackage[LsposedIdentity.normalize(packageName)],
            )
        }.sortedWith(compareByDescending<LsposedInstalledModule> { it.hasUpdate }.thenBy { it.displayName.lowercase() })
    }

    fun providerStatus(): LsposedProviderStatus {
        val managerInstalled = lsposedManagerIntent() != null
        val active = findProviderCandidate(PROVIDER_ACTIVE_ROOT, active = true)
        val staged = findProviderCandidate(PROVIDER_UPDATE_ROOT, active = false)
        val selected = active ?: staged
        val managerOpenMode = when {
            managerInstalled -> LsposedManagerOpenMode.INSTALLED_MANAGER
            active?.actionAvailable == true -> LsposedManagerOpenMode.PROVIDER_ACTION
            selected?.managerApkPresent == true -> LsposedManagerOpenMode.BUNDLED_MANAGER_APK
            else -> LsposedManagerOpenMode.UNAVAILABLE
        }
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
            managerOpenMode = managerOpenMode,
            activeSlot = active?.toSlot(),
            stagedSlot = staged?.toSlot(),
        )
    }

    fun lsposedManagerIntent(): Intent? {
        val pm = context.packageManager
        return LSPOSED_MANAGER_PACKAGES.firstNotNullOfOrNull { packageName ->
            pm.getLaunchIntentForPackage(packageName)
                ?: managerLaunchIntents(packageName).firstOrNull { intent -> resolveActivity(pm, intent) != null }
                ?: managerCategoryLaunchIntent(pm, packageName)
        }
    }

    private fun managerLaunchIntents(packageName: String): List<Intent> = listOf(
        Intent(Intent.ACTION_MAIN)
            .setPackage(packageName)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addCategory("$packageName.LAUNCH_MANAGER"),
        Intent("$packageName.LAUNCH_MANAGER")
            .setPackage(packageName)
            .addCategory(Intent.CATEGORY_DEFAULT),
        Intent("org.lsposed.manager.LAUNCH_MANAGER")
            .setPackage(packageName)
            .addCategory(Intent.CATEGORY_DEFAULT),
    )

    private fun managerCategoryLaunchIntent(pm: PackageManager, packageName: String): Intent? {
        if (!packageInstalled(pm, packageName)) return null
        return managerLaunchIntents(packageName).first()
    }

    @Suppress("DEPRECATION")
    private fun packageInstalled(pm: PackageManager, packageName: String): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            pm.getPackageInfo(packageName, 0)
        }
    }.isSuccess

    @Suppress("DEPRECATION")
    private fun resolveActivity(pm: PackageManager, intent: Intent) = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }.getOrNull()

    fun lsposedProviderActionModuleId(): String? = providerStatus()
        .takeIf { it.actionAvailable }
        ?.moduleId
        ?.takeIf { it.isNotBlank() }

    fun providerRefreshPlan(status: LsposedProviderStatus = providerStatus()): LsposedProviderRefreshPlan = when {
        status.managerPackageInstalled -> LsposedProviderRefreshPlan(LsposedProviderRefreshMode.OPEN_MANAGER)
        status.active && status.actionAvailable && !status.moduleId.isNullOrBlank() -> LsposedProviderRefreshPlan(
            mode = LsposedProviderRefreshMode.ACTION_BRIDGE,
            moduleId = status.moduleId,
        )
        else -> LsposedProviderRefreshPlan(LsposedProviderRefreshMode.REBOOT_REQUIRED)
    }

    fun lsposedProviderRefreshModuleId(): String? = providerRefreshPlan()
        .takeIf { it.mode == LsposedProviderRefreshMode.ACTION_BRIDGE }
        ?.moduleId
        ?.takeIf { it.isNotBlank() }

    fun launchAppIntent(packageName: String): Intent? = context.packageManager.getLaunchIntentForPackage(packageName)

    private fun findProviderCandidate(root: File, active: Boolean): ProviderCandidate? {
        val rootFile = rootAccessFile(root)
        if (!safeIsDirectory(rootFile)) return null

        PROVIDER_MODULE_IDS.asSequence()
            .map { id -> suChild(rootFile, id) }
            .mapNotNull { directory -> providerCandidateFromDirectory(directory, active) }
            .firstOrNull()
            ?.let { return it }

        return runCatching { rootFile.list()?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .asSequence()
            .map { name -> suChild(rootFile, name) }
            .filter(::safeIsDirectory)
            .sortedBy(File::getAbsolutePath)
            .mapNotNull { directory -> providerCandidateFromDirectory(directory, active) }
            .firstOrNull()
    }

    private fun providerCandidateFromDirectory(directory: File, active: Boolean): ProviderCandidate? {
        if (!safeIsDirectory(directory)) return null
        val properties = readModuleProperties(suChild(directory, MODULE_PROP))
        val id = properties["id"].orEmpty().ifBlank { directory.name }
        val name = properties["name"].orEmpty()
        val description = properties["description"].orEmpty()
        val identity = listOf(id, directory.name, name, description)
            .joinToString(" ")
            .lowercase()
        val managerApk = suChild(directory, "manager.apk")
        val lspdDex = suChild(directory, "framework/lspd.dex")
        val daemonApk = suChild(directory, "daemon.apk")
        val hasProviderFiles = safeIsFile(managerApk) || safeIsFile(lspdDex) || safeIsFile(daemonApk)
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
            disabled = safeIsFile(suChild(directory, "disable")),
            // Contract equivalent: actionAvailable = active && File(directory, "action.sh").isFile
            actionAvailable = active && safeIsFile(suChild(directory, "action.sh")),
            // Contract equivalent: managerApkPresent = File(directory, "manager.apk").isFile
            managerApkPresent = safeIsFile(managerApk),
        )
    }

    private fun ProviderCandidate.toSlot(): LsposedProviderSlot = LsposedProviderSlot(
        moduleId = moduleId,
        folder = folder,
        name = name,
        version = version,
        disabled = disabled,
        actionAvailable = actionAvailable,
        managerApkPresent = managerApkPresent,
    )

    @Suppress("DEPRECATION")
    private fun verifyDownloadedApk(file: File, module: LsposedRepoModule): LsposedPreparedApkIdentity {
        val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_META_DATA)
        } ?: error("Downloaded APK could not be parsed")
        val versionCode = PackageInfoCompat.getLongVersionCode(info)
        val expectedVersionCode = module.latestStableVersion?.versionCode
        LsposedApkIdentityPolicy.requireMatches(
            expectedPackageName = module.packageName,
            actualPackageName = info.packageName,
            expectedVersionCode = expectedVersionCode,
            actualVersionCode = versionCode,
        )
        return LsposedPreparedApkIdentity(
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = versionCode,
            sha256 = sha256(file),
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

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

    private fun safeIsDirectory(file: File): Boolean = runCatching { file.isDirectory }.getOrDefault(false)

    private fun safeIsFile(file: File): Boolean = runCatching { file.isFile }.getOrDefault(false)

    private data class CachedRepositoryBody(
        val body: String,
        val fetchedAt: Long,
        val sourceUrl: String,
        val freshness: LsposedCacheFreshness,
        val errorMessage: String? = null,
    )

    private fun readCacheFetchedAt(meta: File): Long = runCatching {
        JSONObject(meta.readText()).optLong("fetched_at", meta.lastModified().takeIf { it > 0L } ?: 0L)
    }.getOrDefault(meta.lastModified().takeIf { it > 0L } ?: 0L)

    private fun readCacheSourceUrl(meta: File): String = runCatching {
        JSONObject(meta.readText()).optString("source_url", LSPOSED_MODULES_URL)
    }.getOrDefault(LSPOSED_MODULES_URL)

    private fun writeAtomic(file: File, value: String) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(value.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun requestTextWithSource(
        url: String,
        fallbackUrls: List<String> = emptyList(),
    ): CachedRepositoryBody {
        val candidates = (listOf(url) + fallbackUrls).distinct()
        val failures = mutableListOf<String>()
        for (candidate in candidates) {
            val result = runCatching { executeJsonRequest(candidate) }
            result.getOrNull()?.let { body ->
                return CachedRepositoryBody(
                    body = body,
                    fetchedAt = System.currentTimeMillis(),
                    sourceUrl = candidate,
                    freshness = LsposedCacheFreshness.FRESH,
                )
            }
            failures += "${candidate}: ${result.exceptionOrNull()?.message ?: "unknown failure"}"
        }
        error(
            buildString {
                append("Unable to load LSPosed repository")
                if (failures.isNotEmpty()) {
                    append(". Tried ")
                    append(failures.joinToString("; "))
                }
            },
        )
    }

    private fun requestText(
        url: String,
        fallbackUrls: List<String> = emptyList(),
    ): String {
        val candidates = (listOf(url) + fallbackUrls).distinct()
        val failures = mutableListOf<String>()
        for (candidate in candidates) {
            val result = runCatching { executeJsonRequest(candidate) }
            result.getOrNull()?.let { return it }
            failures += "${candidate}: ${result.exceptionOrNull()?.message ?: "unknown failure"}"
        }
        error(
            buildString {
                append("Unable to load LSPosed repository")
                if (failures.isNotEmpty()) {
                    append(". Tried ")
                    append(failures.joinToString("; "))
                }
            },
        )
    }

    private fun executeJsonRequest(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent())
            .applyGitHubAuthentication(url, githubTokenStore.getToken())
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val snippet = NetworkPolicy.readErrorSnippet(response.body).take(220).trim().takeIf(String::isNotBlank)
                error(lsposedRepositoryFailureMessage(url, response.code, snippet))
            }
            return response.body?.let { NetworkPolicy.readUtf8Bounded(it, NetworkPolicy.MAX_REPOSITORY_JSON_BYTES, url) }
                ?: error("empty response")
        }
    }

    private fun Request.Builder.applyGitHubAuthentication(
        requestUrl: String,
        githubToken: String?,
    ): Request.Builder = apply {
        if (NetworkPolicy.shouldAttachGitHubToken(requestUrl)) {
            githubToken?.trim()?.takeIf(String::isNotBlank)?.let {
                header("Authorization", "Bearer $it")
            }
        }
        val host = runCatching { URI(requestUrl).host.orEmpty() }.getOrDefault("")
        if (host.equals("api.github.com", ignoreCase = true)) {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
    }

    private fun lsposedRepositoryFailureMessage(
        url: String,
        code: Int,
        bodySnippet: String?,
    ): String {
        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
        val guidance = when {
            code == 403 && host.equals("modules.lsposed.org", ignoreCase = true) ->
                "HTTP 403 from modules.lsposed.org; mirror fallback will be tried"
            code == 403 && NetworkPolicy.shouldAttachGitHubToken(url) && !githubTokenStore.hasToken() ->
                "HTTP 403 from GitHub; save a GitHub API token in Settings > Other and retry"
            code == 403 && NetworkPolicy.shouldAttachGitHubToken(url) ->
                "HTTP 403 from GitHub; check the saved token permissions or rate limit"
            else -> "HTTP $code"
        }
        return buildString {
            append(guidance)
            if (!bodySnippet.isNullOrBlank()) {
                append(". Server said: ")
                append(bodySnippet)
            }
        }
    }

    private fun userAgent(): String = "MMRL/${context.packageName} Android"

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
        val identity: LsposedPreparedApkIdentity,
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
        private const val VECTOR_MANAGER_PACKAGE = "org.matrix.vector.manager"

        val LSPOSED_MANAGER_PACKAGES = listOf(
            "org.lsposed.manager",
            "io.github.libxposed.manager",
            "org.lsposed.lspd",
            VECTOR_MANAGER_PACKAGE,
        )

        private const val LSPOSED_MODULES_URL = "https://modules.lsposed.org/modules.json"
        private val LSPOSED_MODULES_FALLBACK_URLS = listOf(
            "https://backup.modules.lsposed.org/modules.json",
            "https://cdn.jsdelivr.net/gh/Xposed-Modules-Repo/modules@gh-pages/modules.json",
        )

        fun lsposedModuleFallbackUrls(packageName: String): List<String> = listOf(
            "https://backup.modules.lsposed.org/module/$packageName.json",
            "https://cdn.jsdelivr.net/gh/Xposed-Modules-Repo/modules@gh-pages/module/$packageName.json",
            "https://cdn.jsdelivr.net/gh/Xposed-Modules-Repo/modules@gh-pages/$packageName.json",
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
        private const val DATA_ADB_ROOT = "/data/adb/"

        fun packageInstallerIntent(uri: Uri): Intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

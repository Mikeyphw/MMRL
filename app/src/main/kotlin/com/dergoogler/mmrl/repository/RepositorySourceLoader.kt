package com.dergoogler.mmrl.repository

import com.dergoogler.mmrl.app.moshi
import com.dergoogler.mmrl.github.GitHubModuleRequest
import com.dergoogler.mmrl.github.GitHubModuleResolver
import com.dergoogler.mmrl.github.GitHubSourceMode
import com.dergoogler.mmrl.model.online.ModulesJson
import com.dergoogler.mmrl.model.online.ModulesJsonMetadata
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.model.online.TrackJson
import com.dergoogler.mmrl.model.online.TrackType
import com.dergoogler.mmrl.model.online.VersionItem
import com.dergoogler.mmrl.network.NetworkUtils
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Loads both native MMRL repository objects and KernelSU-style bare module arrays. */
internal object RepositorySourceLoader {
    private const val MAX_CONCURRENT_RELEASE_LOOKUPS = 6

    private val modulesAdapter by lazy { moshi.adapter(ModulesJson::class.java) }
    private val kernelSuCatalogAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, KernelSuCatalogEntry::class.java)
        moshi.adapter<List<KernelSuCatalogEntry>>(type)
    }
    private val httpClient by lazy { NetworkUtils.createOkHttpClient() }
    private val noRedirectClient by lazy {
        httpClient
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun load(
        repoUrl: String,
        githubToken: String? = null,
    ): Result<ModulesJson> =
        withContext(Dispatchers.IO) {
            runCatching {
                parseGitHubSourceOptions(repoUrl)?.let { options ->
                    return@runCatching loadGitHubSource(options, githubToken)
                }
                val sourceUrl = resolveModulesUrl(repoUrl)
                val json = fetchText(sourceUrl, githubToken)
                when (json.firstOrNull { !it.isWhitespace() }) {
                    '{' ->
                        modulesAdapter.fromJson(json)
                            ?: error("Repository returned an empty JSON object")
                    '[' -> loadKernelSuCatalog(sourceUrl, json, githubToken)
                    else -> error("Unsupported repository JSON: expected an object or array")
                }
            }
        }

    private suspend fun loadGitHubSource(
        options: GitHubSourceOptions,
        githubToken: String?,
    ): ModulesJson {
        val resolver = GitHubModuleResolver()
        val result =
            resolver.resolve(
                GitHubModuleRequest(
                    repoUrl = options.repoUrl,
                    mode = options.mode,
                    includePreReleases = options.includePreReleases,
                    regex = options.regex,
                    token = githubToken,
                ),
            )
        val candidate = result.recommended ?: error("GitHub repository has no installable files")
        val github = parseGitHubRepository(options.repoUrl)
        val properties =
            if (options.mode == GitHubSourceMode.RELEASE) {
                resolveModuleProperties(github.baseUrl, candidate.version, githubToken)
            } else {
                resolveModuleProperties(github.baseUrl, "HEAD", githubToken)
            }

        val moduleId =
            properties["id"]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: syntheticModuleId(github.owner, github.repository)
        val moduleName =
            properties["name"]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: github.repository
        val moduleVersion =
            properties["version"]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: candidate.version
        val moduleVersionCode =
            if (options.mode == GitHubSourceMode.NIGHTLY) {
                candidate.versionCode
            } else {
                properties["versionCode"]
                    ?.trim()
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?: candidate.versionCode
            }
        val moduleAuthor =
            properties["author"]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: github.owner
        val moduleDescription =
            properties["description"]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: "GitHub ${if (options.mode == GitHubSourceMode.NIGHTLY) "nightly" else "release"} module source."

        val version =
            VersionItem(
                timestamp = System.currentTimeMillis() / 1000f,
                version = moduleVersion,
                versionCode = moduleVersionCode,
                zipUrl = candidate.apiDownloadUrl ?: candidate.downloadUrl,
                size = candidate.size?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                changelog = candidate.sourceName,
            )

        return ModulesJson(
            name = "GitHub: ${github.owner}/${github.repository}",
            submission = github.baseUrl,
            website = github.baseUrl,
            description =
                "Persistent GitHub module source using " +
                    if (options.mode == GitHubSourceMode.NIGHTLY) {
                        "the latest successful Actions artifact."
                    } else {
                        "GitHub releases."
                    },
            metadata =
                ModulesJsonMetadata(
                    version = ModulesJson.CURRENT_VERSION,
                    timestamp = System.currentTimeMillis() / 1000f,
                ),
            modules =
                listOf(
                    OnlineModule(
                        id = moduleId,
                        name = moduleName,
                        version = moduleVersion,
                        versionCode = moduleVersionCode,
                        author = moduleAuthor,
                        description = moduleDescription,
                        track = TrackJson(typeName = TrackType.GIT.name, source = github.baseUrl),
                        versions = listOf(version),
                        homepage = github.baseUrl,
                        support = "${github.baseUrl}/issues",
                    ),
                ),
        )
    }

    internal fun resolveModulesUrl(repoUrl: String): String {
        val trimmed = repoUrl.trim()
        require(NetworkUtils.isUrl(trimmed)) { "Invalid repository URL: $repoUrl" }

        val path = runCatching { URI(trimmed).path.orEmpty() }.getOrDefault("")
        return if (path.endsWith(".json", ignoreCase = true)) {
            trimmed
        } else {
            "${trimmed.trimEnd('/')}/json/modules.json"
        }
    }

    private suspend fun loadKernelSuCatalog(
        sourceUrl: String,
        json: String,
        githubToken: String?,
    ): ModulesJson {
        val entries =
            parseKernelSuCatalog(json)
                .filter { it.visibility == 1 && it.repoUrl.isNotBlank() }
                .distinctBy { it.repoUrl.trimEnd('/').lowercase(Locale.ROOT) }

        require(entries.isNotEmpty()) { "KernelSU-style repository contains no visible modules" }

        val semaphore = Semaphore(MAX_CONCURRENT_RELEASE_LOOKUPS)
        val modules =
            coroutineScope {
                entries
                    .map { entry ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                runCatching { resolveKernelSuEntry(entry, githubToken) }
                                    .onFailure {
                                        Timber.w(
                                            it,
                                            "Skipping incompatible KernelSU repository entry: ${entry.repoUrl}",
                                        )
                                    }.getOrNull()
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
            }

        require(modules.isNotEmpty()) {
            "No installable ZIP releases could be resolved from this KernelSU-style repository"
        }

        return ModulesJson(
            name = catalogName(sourceUrl),
            submission = catalogWebsite(sourceUrl),
            website = catalogWebsite(sourceUrl),
            description =
                "KernelSU-compatible GitHub module catalog. " +
                    "Module metadata and ZIP files are resolved from each repository's latest release.",
            metadata =
                ModulesJsonMetadata(
                    version = ModulesJson.CURRENT_VERSION,
                    timestamp = System.currentTimeMillis() / 1000f,
                ),
            modules = modules,
        )
    }

    private fun resolveKernelSuEntry(
        entry: KernelSuCatalogEntry,
        githubToken: String?,
    ): OnlineModule {
        val github = parseGitHubRepository(entry.repoUrl)
        val release = resolveLatestRelease(github.baseUrl, githubToken)
        val properties = resolveModuleProperties(github.baseUrl, release.tag, githubToken)

        val moduleId =
            properties["id"]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: syntheticModuleId(github.owner, github.repository)
        val moduleName =
            properties["name"]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: entry.name.ifBlank { github.repository }
        val moduleVersion =
            properties["version"]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: release.tag
        val moduleVersionCode =
            properties["versionCode"]
                ?.trim()
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: syntheticVersionCode(release.tag)
        val moduleAuthor =
            properties["author"]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: entry.author.orEmpty().ifBlank { github.owner }
        val moduleDescription =
            properties["description"]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: entry.description

        val version =
            VersionItem(
                timestamp = 0f,
                version = moduleVersion,
                versionCode = moduleVersionCode,
                zipUrl = release.zipUrl,
            )

        return OnlineModule(
            id = moduleId,
            name = moduleName,
            version = moduleVersion,
            versionCode = moduleVersionCode,
            author = moduleAuthor,
            description = moduleDescription,
            track =
                TrackJson(
                    typeName = TrackType.GIT.name,
                    source = github.baseUrl,
                ),
            versions = listOf(version),
            homepage = github.baseUrl,
            support = "${github.baseUrl}/issues",
            cover = entry.bannerUrl,
            license = entry.license,
        )
    }

    private fun resolveLatestRelease(
        repoUrl: String,
        githubToken: String?,
    ): GitHubRelease {
        val latestRequest =
            Request
                .Builder()
                .url("$repoUrl/releases/latest")
                .applyGitHubAuthentication("$repoUrl/releases/latest", githubToken)
                .build()
        val redirect =
            noRedirectClient.newCall(latestRequest).execute().use { response ->
                require(response.code in 300..399) {
                    "GitHub repository has no latest-release redirect: $repoUrl"
                }
                response.header("Location")
                    ?: error("GitHub latest release did not provide a redirect: $repoUrl")
            }

        val encodedTag =
            Regex("/releases/tag/([^/?#]+)")
                .find(redirect)
                ?.groupValues
                ?.get(1)
                ?: error("Unable to determine latest release tag for $repoUrl")
        val tag = URLDecoder.decode(encodedTag, StandardCharsets.UTF_8.name())
        val assetsUrl = "$repoUrl/releases/expanded_assets/${encodePathSegment(tag)}"
        val assetsHtml = fetchText(assetsUrl, githubToken)
        val zipPath =
            Regex(
                """href=[\"'](/[^\"']+/releases/download/[^\"']+\.zip(?:\?[^\"']*)?)[\"']""",
                RegexOption.IGNORE_CASE,
            ).find(assetsHtml)
                ?.groupValues
                ?.get(1)
                ?.replace("&amp;", "&")
                ?: error("Latest GitHub release has no ZIP asset: $repoUrl")

        return GitHubRelease(
            tag = tag,
            zipUrl = "https://github.com$zipPath",
        )
    }

    private fun resolveModuleProperties(
        repoUrl: String,
        tag: String,
        githubToken: String? = null,
    ): Map<String, String> {
        val encodedTag = encodePathSegment(tag)
        val candidates =
            listOf(
                "$repoUrl/raw/$encodedTag/module.prop",
                "$repoUrl/raw/$encodedTag/module/module.prop",
                "$repoUrl/raw/HEAD/module.prop",
                "$repoUrl/raw/HEAD/module/module.prop",
            )

        return candidates.firstNotNullOfOrNull { url ->
            runCatching { parseModuleProperties(fetchText(url, githubToken)) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    internal fun parseKernelSuCatalog(json: String): List<KernelSuCatalogEntry> =
        kernelSuCatalogAdapter.fromJson(json).orEmpty()

    internal fun parseModuleProperties(text: String): Map<String, String> =
        text
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') && '=' in it }
            .map { line ->
                val separator = line.indexOf('=')
                line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }.filter { (key, _) -> key.isNotEmpty() }
            .toMap()

    internal fun syntheticVersionCode(tag: String): Int {
        val numbers =
            Regex("\\d+")
                .findAll(tag)
                .mapNotNull { it.value.toLongOrNull() }
                .take(3)
                .toList()

        if (numbers.isNotEmpty()) {
            val value =
                numbers.fold(0L) { current, number ->
                    (current * 1000L + number.coerceIn(0L, 999L)).coerceAtMost(Int.MAX_VALUE.toLong())
                }
            if (value > 0) return value.toInt()
        }

        return (tag.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)
    }

    private fun syntheticModuleId(
        owner: String,
        repository: String,
    ): String =
        "$owner.$repository"
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9._-]"), "_")

    private fun parseGitHubRepository(repoUrl: String): GitHubRepository {
        val normalized = repoUrl.trim().trimEnd('/').removeSuffix(".git")
        val match =
            Regex("^https://github\\.com/([^/]+)/([^/?#]+)$", RegexOption.IGNORE_CASE)
                .matchEntire(normalized)
                ?: error("Only GitHub repository URLs are supported: $repoUrl")

        val owner = match.groupValues[1]
        val repository = match.groupValues[2]
        return GitHubRepository(
            owner = owner,
            repository = repository,
            baseUrl = "https://github.com/$owner/$repository",
        )
    }

    private fun parseGitHubSourceOptions(repoUrl: String): GitHubSourceOptions? {
        val uri = runCatching { URI(repoUrl.trim()) }.getOrNull() ?: return null
        if (!uri.host.equals("github.com", ignoreCase = true)) return null
        val parts = uri.path.trim('/').split('/').filter(String::isNotBlank)
        if (parts.size < 2) return null
        val parameters =
            uri.rawQuery
                ?.split('&')
                .orEmpty()
                .mapNotNull { item ->
                    val key = item.substringBefore('=', "").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val value = item.substringAfter('=', "")
                    URLDecoder.decode(key, StandardCharsets.UTF_8.name()) to
                        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
                }.toMap()
        val mode =
            when (parameters["mmrlSource"]?.lowercase(Locale.ROOT)) {
                "nightly" -> GitHubSourceMode.NIGHTLY
                else -> GitHubSourceMode.RELEASE
            }
        val cleanRepoUrl = "https://github.com/${parts[0]}/${parts[1]}"
        return GitHubSourceOptions(
            repoUrl = cleanRepoUrl,
            mode = mode,
            includePreReleases = parameters["includePreReleases"].equals("true", ignoreCase = true),
            regex = parameters["regex"].orEmpty(),
        )
    }

    private fun fetchText(
        url: String,
        githubToken: String? = null,
    ): String {
        val request =
            Request
                .Builder()
                .url(url)
                .applyGitHubAuthentication(url, githubToken)
                .build()
        return httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code} while loading $url" }
            response.body?.string() ?: error("Empty response while loading $url")
        }
    }

    private fun Request.Builder.applyGitHubAuthentication(
        requestUrl: String,
        githubToken: String?,
    ): Request.Builder =
        apply {
            val host = runCatching { URI(requestUrl).host.orEmpty() }.getOrDefault("")
            if (host.contains("github", ignoreCase = true)) {
                githubToken?.trim()?.takeIf(String::isNotBlank)?.let {
                    header("Authorization", "Bearer $it")
                }
                if (host.equals("api.github.com", ignoreCase = true)) {
                    header("Accept", "application/vnd.github+json")
                    header("X-GitHub-Api-Version", "2022-11-28")
                }
            }
        }

    private fun catalogName(sourceUrl: String): String =
        if (sourceUrl.contains("KernelSU-Next-Modules-Repo", ignoreCase = true)) {
            "KernelSU Next Modules Repo"
        } else {
            "KernelSU-compatible Modules"
        }

    private fun catalogWebsite(sourceUrl: String): String? {
        val match =
            Regex("^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/")
                .find(sourceUrl)
                ?: return null
        return "https://github.com/${match.groupValues[1]}/${match.groupValues[2]}"
    }

    private fun encodePathSegment(value: String): String =
        URLEncoder
            .encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")

    @JsonClass(generateAdapter = true)
    internal data class KernelSuCatalogEntry(
        val name: String = "",
        val description: String? = null,
        val author: String? = null,
        @param:Json(name = "repoUrl") val repoUrl: String = "",
        val license: String? = null,
        @param:Json(name = "bannerUrl") val bannerUrl: String? = null,
        val visibility: Int = 1,
    )

    private data class GitHubRepository(
        val owner: String,
        val repository: String,
        val baseUrl: String,
    )

    private data class GitHubRelease(
        val tag: String,
        val zipUrl: String,
    )

    private data class GitHubSourceOptions(
        val repoUrl: String,
        val mode: GitHubSourceMode,
        val includePreReleases: Boolean,
        val regex: String,
    )
}

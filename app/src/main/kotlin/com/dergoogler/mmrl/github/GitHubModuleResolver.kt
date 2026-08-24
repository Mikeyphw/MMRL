package com.dergoogler.mmrl.github

import android.os.Build
import com.dergoogler.mmrl.app.moshi
import com.dergoogler.mmrl.network.NetworkPolicy
import com.dergoogler.mmrl.network.NetworkUtils
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.net.URI
import java.util.Locale
import kotlin.math.absoluteValue

enum class GitHubSourceMode {
    RELEASE,
    NIGHTLY,
}

data class GitHubModuleRequest(
    val repoUrl: String,
    val mode: GitHubSourceMode,
    val includePreReleases: Boolean,
    val regex: String,
    val token: String?,
    val assetRegex: String = "",
    val artifactRegex: String = "",
    val rejectRegex: String = "",
    val preferredVariantRegex: String = "",
    val branchRegex: String = "",
    val workflowRegex: String = "",
    val artifactStrategy: GitHubArtifactStrategy = GitHubArtifactStrategy.AUTO,
)

data class GitHubCandidate(
    val id: String,
    val name: String,
    val sourceName: String,
    val version: String,
    val versionCode: Int,
    val downloadUrl: String,
    val apiDownloadUrl: String? = null,
    val size: Long?,
    val updatedAt: String?,
    val mode: GitHubSourceMode,
    val score: Int,
    val nestedZipName: String? = null,
    val artifactStrategy: GitHubArtifactStrategy = GitHubArtifactStrategy.AUTO,
    val sourceCommit: String? = null,
    val workflowRunId: Long? = null,
    val artifactId: Long? = null,
    val diagnostics: String? = null,
) {
    fun provenanceSummary(): String =
        listOfNotNull(
            "mode=${mode.name.lowercase(Locale.ROOT)}",
            "id=$id",
            sourceCommit?.let { "commit=$it" },
            workflowRunId?.let { "run=$it" },
            artifactId?.let { "artifact=$it" },
            diagnostics?.let { "diagnostics=$it" },
        ).joinToString("; ")
}

data class GitHubResolveResult(
    val repository: String,
    val candidates: List<GitHubCandidate>,
    val recommended: GitHubCandidate?,
)

internal object GitHubReleaseSelectionPolicy {
    fun select(
        releases: List<GitHubModuleResolver.GitHubRelease>,
        includePreReleases: Boolean,
        acceptAsset: (GitHubModuleResolver.GitHubAsset) -> Boolean,
    ): Pair<GitHubModuleResolver.GitHubRelease, List<GitHubModuleResolver.GitHubAsset>>? {
        releases.forEach { release ->
            if (release.draft || (!includePreReleases && release.prerelease)) return@forEach
            val assets = release.assets.filter(acceptAsset)
            if (assets.isNotEmpty()) return release to assets
        }
        return null
    }
}

class GitHubModuleResolver {
    private val client by lazy { NetworkUtils.createOkHttpClient() }
    private val releasesAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, GitHubRelease::class.java)
        moshi.adapter<List<GitHubRelease>>(type)
    }
    private val runsAdapter by lazy { moshi.adapter(GitHubRunsResponse::class.java) }
    private val artifactsAdapter by lazy { moshi.adapter(GitHubArtifactsResponse::class.java) }

    suspend fun resolve(request: GitHubModuleRequest): GitHubResolveResult =
        withContext(Dispatchers.IO) {
            val repo = parseRepository(request.repoUrl)
            val candidates =
                when (request.mode) {
                    GitHubSourceMode.RELEASE -> resolveReleases(repo, request.includePreReleases, request.token, request)
                    GitHubSourceMode.NIGHTLY -> resolveNightly(repo, request.token, request)
                }.sortedWith(compareByDescending<GitHubCandidate> { it.score }.thenByDescending { it.updatedAt.orEmpty() })

            require(candidates.isNotEmpty()) {
                if (request.hasAnyMatcher()) {
                    "No GitHub files matched the saved source rules"
                } else {
                    "No installable GitHub files found"
                }
            }

            GitHubResolveResult(
                repository = repo.slug,
                candidates = candidates,
                recommended = candidates.firstOrNull(),
            )
        }

    suspend fun downloadCandidate(
        cacheDir: File,
        candidate: GitHubCandidate,
        token: String?,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val root = File(cacheDir, "github-modules").apply { mkdirs() }
        val downloaded = File(root, safeFileName("${candidate.id}-${candidate.name}"))
        download(
            url = candidate.downloadUrl,
            apiUrl = candidate.apiDownloadUrl,
            destination = downloaded,
            token = token,
            onProgress = onProgress,
        )

        val materialized = GitHubArtifactArchivePolicy.materializeModuleZip(
            archive = downloaded,
            targetDirectory = root,
            outputNamePrefix = candidate.id,
            preferredEntryName = candidate.nestedZipName,
            forcedStrategy = candidate.artifactStrategy,
            score = ::assetScore,
        )
        materialized.file
    }

    private fun resolveReleases(
        repo: GitHubRepository,
        includePreReleases: Boolean,
        token: String?,
        request: GitHubModuleRequest,
    ): List<GitHubCandidate> {
        val releases =
            (1..NetworkPolicy.MAX_GITHUB_API_PAGES)
                .flatMap { page ->
                    releasesAdapter.fromJson(apiText(repo, "releases?per_page=30&page=$page", token)).orEmpty()
                }
        val nameRule = request.assetNameRegex()
        val rejectRule = request.rejectNameRegex()
        val preferredRule = request.preferredNameRegex()
        val selected = GitHubReleaseSelectionPolicy.select(
            releases = releases.distinctBy { it.id },
            includePreReleases = includePreReleases,
        ) { asset ->
            asset.name.endsWith(".zip", ignoreCase = true) &&
                (nameRule?.containsMatchIn(asset.name) ?: true) &&
                !(rejectRule?.containsMatchIn(asset.name) ?: false)
        } ?: error("No GitHub release contains an installable file matching the saved source rules")

        val (release, assets) = selected
        return assets.map { asset ->
            val score = assetScore(asset.name) + if (preferredRule?.containsMatchIn(asset.name) == true) 240 else 0
            GitHubCandidate(
                id = "release-${release.id}-${asset.id}",
                name = asset.name,
                sourceName = release.name?.takeIf(String::isNotBlank) ?: release.tagName,
                version = release.tagName,
                versionCode = syntheticVersionCode(release.tagName),
                downloadUrl = asset.browserDownloadUrl ?: asset.url,
                apiDownloadUrl = asset.url,
                size = asset.size,
                updatedAt = asset.updatedAt ?: release.publishedAt,
                mode = GitHubSourceMode.RELEASE,
                score = score,
                artifactStrategy = request.artifactStrategy,
                artifactId = asset.id,
                diagnostics = "release asset matched saved source rules; strategy=${request.artifactStrategy.queryValue}; release=${release.id}; asset=${asset.id}",
            )
        }
    }

    private fun resolveNightly(
        repo: GitHubRepository,
        token: String?,
        request: GitHubModuleRequest,
    ): List<GitHubCandidate> {
        val runs =
            (1..NetworkPolicy.MAX_GITHUB_API_PAGES)
                .flatMap { page ->
                    runsAdapter
                        .fromJson(apiText(repo, "actions/runs?status=success&per_page=20&page=$page", token))
                        ?.workflowRuns
                        .orEmpty()
                }
                .filter { run -> request.branchNameRegex()?.containsMatchIn(run.headBranch.orEmpty()) ?: true }
                .filter { run ->
                    val workflow = listOf(run.name, run.path.orEmpty()).joinToString(" ")
                    request.workflowNameRegex()?.containsMatchIn(workflow) ?: true
                }
                .sortedByDescending { it.createdAt.orEmpty() }

        val artifactRule = request.artifactNameRegex()
        val rejectRule = request.rejectNameRegex()
        val preferredRule = request.preferredNameRegex()

        runs.forEach { run ->
            val artifacts =
                fetchArtifacts(run.artifactsUrl, token)
                    .filter { !it.expired }
                    .filter { artifact -> artifactRule?.containsMatchIn(artifact.name) ?: true }
                    .filterNot { artifact -> rejectRule?.containsMatchIn(artifact.name) ?: false }
            if (artifacts.isNotEmpty()) {
                return artifacts.map { artifact ->
                    GitHubCandidate(
                        id = "artifact-${run.id}-${artifact.id}",
                        name = artifact.name,
                        sourceName = run.name.ifBlank { "Nightly" },
                        version = run.headSha.take(7).ifBlank { "nightly" },
                        versionCode = run.runNumber.takeIf { it > 0 } ?: run.id.hashCode().absoluteValue.coerceAtLeast(1),
                        downloadUrl = artifact.archiveDownloadUrl,
                        apiDownloadUrl = artifact.archiveDownloadUrl,
                        size = artifact.sizeInBytes,
                        updatedAt = artifact.updatedAt ?: run.updatedAt,
                        mode = GitHubSourceMode.NIGHTLY,
                        score = assetScore(artifact.name) + if (preferredRule?.containsMatchIn(artifact.name) == true) 240 else 0,
                        artifactStrategy = request.artifactStrategy,
                        sourceCommit = run.headSha.takeIf(String::isNotBlank),
                        workflowRunId = run.id,
                        artifactId = artifact.id,
                        diagnostics = "nightly artifact matched saved source rules; strategy=${request.artifactStrategy.queryValue}; run=${run.name}; branch=${run.headBranch.orEmpty()}; sha=${run.headSha}; artifact=${artifact.id}",
                    )
                }
            }
        }

        error("No successful GitHub Actions run with artifacts matching saved source rules found")
    }

    private fun download(
        url: String,
        apiUrl: String?,
        destination: File,
        token: String?,
        onProgress: (Float) -> Unit,
    ) {
        val downloadUrl = apiUrl ?: url
        val response = execute(downloadUrl, token, acceptForDownload(downloadUrl))
        response.use {
            NetworkPolicy.requireSuccessful(it)
            val body = it.body ?: error("Empty download response")
            val length = body.contentLength()
            require(NetworkPolicy.declaredDownloadLengthAllowed(length)) {
                "Download exceeds the ${NetworkPolicy.MAX_DOWNLOAD_BYTES} byte safety limit"
            }
            var finished = 0L
            destination.delete()
            try {
                body.byteStream().buffered().use { input ->
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            if (read == 0) continue
                            finished = NetworkPolicy.addReceivedBytes(finished, read)
                            output.write(buffer, 0, read)
                            if (length > 0L) onProgress((finished.toDouble() / length).toFloat().coerceIn(0f, 1f))
                        }
                    }
                }
            } catch (error: Throwable) {
                destination.delete()
                throw error
            }
        }
        require(destination.isFile && destination.length() > 0L) { "Downloaded file is empty" }
    }

    private fun GitHubModuleRequest.hasAnyMatcher(): Boolean =
        listOf(regex, assetRegex, artifactRegex, rejectRegex, preferredVariantRegex, branchRegex, workflowRegex)
            .any { it.isNotBlank() }

    private fun GitHubModuleRequest.assetNameRegex(): Regex? =
        compileRule(assetRegex.ifBlank { regex }, "asset regex")

    private fun GitHubModuleRequest.artifactNameRegex(): Regex? =
        compileRule(artifactRegex.ifBlank { regex }, "artifact regex")

    private fun GitHubModuleRequest.rejectNameRegex(): Regex? =
        compileRule(rejectRegex, "reject regex")

    private fun GitHubModuleRequest.preferredNameRegex(): Regex? =
        compileRule(preferredVariantRegex, "preferred variant regex")

    private fun GitHubModuleRequest.branchNameRegex(): Regex? =
        compileRule(branchRegex, "branch regex")

    private fun GitHubModuleRequest.workflowNameRegex(): Regex? =
        compileRule(workflowRegex, "workflow regex")

    private fun compileRule(value: String, label: String): Regex? {
        val clean = value.trim().takeIf(String::isNotBlank) ?: return null
        require(clean.length <= 240) { "$label is too long" }
        return runCatching { Regex(clean, RegexOption.IGNORE_CASE) }
            .getOrElse { error("Invalid $label: ${it.message}") }
    }

    private fun apiText(
        repo: GitHubRepository,
        path: String,
        token: String?,
    ): String = fetchText("https://api.github.com/repos/${repo.owner}/${repo.name}/$path", token)

    private fun fetchArtifacts(
        artifactsUrl: String,
        token: String?,
    ): List<GitHubArtifact> =
        (1..NetworkPolicy.MAX_GITHUB_API_PAGES)
            .flatMap { page ->
                artifactsAdapter
                    .fromJson(fetchText(appendPageQuery(artifactsUrl, page), token))
                    ?.artifacts
                    .orEmpty()
            }.distinctBy { it.id }

    private fun appendPageQuery(
        url: String,
        page: Int,
    ): String {
        val separator = if ('?' in url) "&" else "?"
        return "$url${separator}per_page=100&page=$page"
    }

    private fun fetchText(
        url: String,
        token: String?,
    ): String {
        val response = execute(url, token, "application/vnd.github+json")
        response.use {
            NetworkPolicy.requireSuccessful(it)
            return it.body?.let { body ->
                NetworkPolicy.readUtf8Bounded(body, NetworkPolicy.MAX_GITHUB_JSON_BYTES, url)
            } ?: error("GitHub returned an empty response")
        }
    }

    private fun execute(
        url: String,
        token: String?,
        accept: String,
    ): Response {
        val builder =
            Request
                .Builder()
                .url(url)
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
        if (NetworkPolicy.shouldAttachGitHubToken(url)) {
            token?.trim()?.takeIf(String::isNotBlank)?.let {
                builder.header("Authorization", "Bearer $it")
            }
        }
        return client.newCall(builder.build()).execute()
    }

    private fun acceptForDownload(url: String): String =
        if (GitHubArtifactArchivePolicy.isActionsArtifactArchive(url)) {
            "application/vnd.github+json"
        } else {
            "application/octet-stream"
        }

    private fun parseRepository(repoUrl: String): GitHubRepository {
        val normalized = repoUrl.trim().trimEnd('/').removeSuffix(".git")
        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid GitHub repository URL")
        require(uri.host.equals("github.com", ignoreCase = true)) {
            "Only github.com repositories are supported"
        }
        val parts = uri.path.trim('/').split('/').filter(String::isNotBlank)
        require(parts.size >= 2) { "GitHub URL must include owner and repository" }
        return GitHubRepository(parts[0], parts[1])
    }

    private fun assetScore(name: String): Int {
        val lower = name.lowercase(Locale.ROOT)
        val abiScore =
            Build.SUPPORTED_ABIS
                .flatMap(::abiAliases)
                .distinct()
                .mapIndexedNotNull { index, alias ->
                    if (lower.contains(alias)) 400 - index else null
                }.maxOrNull() ?: 0
        val moduleScore = if (lower.endsWith(".zip")) 40 else 0
        val genericPenalty =
            when {
                lower.contains("source") || lower.contains("symbols") || lower.contains("debug") -> -80
                else -> 0
            }
        return abiScore + moduleScore + genericPenalty
    }

    private fun abiAliases(abi: String): List<String> =
        when (abi.lowercase(Locale.ROOT)) {
            "arm64-v8a" -> listOf("arm64-v8a", "aarch64", "arm64")
            "armeabi-v7a" -> listOf("armeabi-v7a", "armv7", "arm")
            "x86_64" -> listOf("x86_64", "amd64")
            else -> listOf(abi.lowercase(Locale.ROOT))
        }

    private fun syntheticVersionCode(tag: String): Int {
        val numbers = Regex("\\d+").findAll(tag).mapNotNull { it.value.toLongOrNull() }.take(3).toList()
        if (numbers.isNotEmpty()) {
            val value = numbers.fold(0L) { current, number -> (current * 1000L + number.coerceIn(0L, 999L)).coerceAtMost(Int.MAX_VALUE.toLong()) }
            if (value > 0L) return value.toInt()
        }
        return (tag.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)
    }

    private fun safeFileName(value: String): String =
        value
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "github-module.zip" }

    private data class GitHubRepository(
        val owner: String,
        val name: String,
    ) {
        val slug get() = "$owner/$name"
    }

    @JsonClass(generateAdapter = true)
    internal data class GitHubRelease(
        val id: Long,
        @param:Json(name = "tag_name") val tagName: String,
        val name: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @param:Json(name = "published_at") val publishedAt: String? = null,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    internal data class GitHubAsset(
        val id: Long,
        val name: String,
        val url: String,
        @param:Json(name = "browser_download_url") val browserDownloadUrl: String? = null,
        val size: Long? = null,
        @param:Json(name = "updated_at") val updatedAt: String? = null,
    )

    @JsonClass(generateAdapter = true)
    internal data class GitHubRunsResponse(
        @param:Json(name = "workflow_runs") val workflowRuns: List<GitHubRun> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    internal data class GitHubRun(
        val id: Long,
        val name: String = "",
        @param:Json(name = "run_number") val runNumber: Int = 0,
        @param:Json(name = "head_sha") val headSha: String = "",
        @param:Json(name = "head_branch") val headBranch: String? = null,
        val path: String? = null,
        @param:Json(name = "artifacts_url") val artifactsUrl: String,
        @param:Json(name = "created_at") val createdAt: String? = null,
        @param:Json(name = "updated_at") val updatedAt: String? = null,
    )

    @JsonClass(generateAdapter = true)
    internal data class GitHubArtifactsResponse(
        @param:Json(name = "total_count") val totalCount: Int = 0,
        val artifacts: List<GitHubArtifact> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    internal data class GitHubArtifact(
        val id: Long,
        val name: String,
        @param:Json(name = "archive_download_url") val archiveDownloadUrl: String,
        @param:Json(name = "size_in_bytes") val sizeInBytes: Long? = null,
        val expired: Boolean = false,
        @param:Json(name = "updated_at") val updatedAt: String? = null,
    )
}

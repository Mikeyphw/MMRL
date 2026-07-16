package com.dergoogler.mmrl.github

import android.os.Build
import com.dergoogler.mmrl.app.moshi
import com.dergoogler.mmrl.network.NetworkUtils
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.zip.ZipFile
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
)

data class GitHubResolveResult(
    val repository: String,
    val candidates: List<GitHubCandidate>,
    val recommended: GitHubCandidate?,
)

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
            val regex = request.regex.trim().takeIf(String::isNotBlank)?.let {
                runCatching { Regex(it, RegexOption.IGNORE_CASE) }
                    .getOrElse { error("Invalid regex: ${it.message}") }
            }
            val candidates =
                when (request.mode) {
                    GitHubSourceMode.RELEASE -> resolveReleases(repo, request.includePreReleases, request.token)
                    GitHubSourceMode.NIGHTLY -> resolveNightly(repo, request.token)
                }.filter { candidate ->
                    regex?.containsMatchIn(candidate.name) ?: true
                }.sortedWith(compareByDescending<GitHubCandidate> { it.score }.thenByDescending { it.updatedAt.orEmpty() })

            require(candidates.isNotEmpty()) {
                if (regex == null) {
                    "No installable ZIP files found"
                } else {
                    "No files matched the regex"
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
            accept = "application/octet-stream",
            onProgress = onProgress,
        )

        if (candidate.mode == GitHubSourceMode.RELEASE && candidate.nestedZipName == null) {
            downloaded
        } else {
            extractNestedZip(
                archive = downloaded,
                targetDirectory = root,
                preferredEntryName = candidate.nestedZipName,
            )
        }
    }

    private fun resolveReleases(
        repo: GitHubRepository,
        includePreReleases: Boolean,
        token: String?,
    ): List<GitHubCandidate> {
        val releases = releasesAdapter.fromJson(apiText(repo, "releases?per_page=30", token)).orEmpty()
        val release =
            releases.firstOrNull { !it.draft && (includePreReleases || !it.prerelease) }
                ?: error("No matching GitHub release found")

        return release.assets
            .filter { it.name.endsWith(".zip", ignoreCase = true) }
            .map { asset ->
                val score = assetScore(asset.name)
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
                )
            }
    }

    private fun resolveNightly(
        repo: GitHubRepository,
        token: String?,
    ): List<GitHubCandidate> {
        val runs =
            runsAdapter
                .fromJson(apiText(repo, "actions/runs?status=success&per_page=20", token))
                ?.workflowRuns
                .orEmpty()
                .sortedByDescending { it.createdAt.orEmpty() }

        runs.forEach { run ->
            val artifacts =
                artifactsAdapter
                    .fromJson(fetchText(run.artifactsUrl, token))
                    ?.artifacts
                    .orEmpty()
                    .filter { !it.expired }
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
                        score = assetScore(artifact.name),
                    )
                }
            }
        }

        error("No successful GitHub Actions run with artifacts found")
    }

    private fun extractNestedZip(
        archive: File,
        targetDirectory: File,
        preferredEntryName: String?,
    ): File {
        ZipFile(archive).use { zip ->
            val entries =
                zip
                    .entries()
                    .asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".zip", ignoreCase = true) }
                    .toList()
            require(entries.isNotEmpty()) { "GitHub Actions artifact does not contain a module ZIP" }
            val entry =
                preferredEntryName?.let { preferred -> entries.firstOrNull { it.name == preferred } }
                    ?: entries.maxBy { assetScore(it.name) }
            val output = File(targetDirectory, safeFileName(entry.name.substringAfterLast('/')))
            zip.getInputStream(entry).buffered().use { input ->
                output.outputStream().buffered().use { outputStream ->
                    input.copyTo(outputStream)
                }
            }
            require(output.isFile && output.length() > 0L) { "Extracted module ZIP is empty" }
            return output
        }
    }

    private fun download(
        url: String,
        apiUrl: String?,
        destination: File,
        token: String?,
        accept: String,
        onProgress: (Float) -> Unit,
    ) {
        val response = execute(apiUrl ?: url, token, accept)
        response.use {
            require(it.isSuccessful) { "HTTP ${it.code} while downloading ${destination.name}" }
            val body = it.body ?: error("Empty download response")
            val length = body.contentLength()
            var finished = 0L
            destination.delete()
            body.byteStream().buffered().use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        finished += read
                        if (length > 0L) onProgress((finished.toDouble() / length).toFloat())
                    }
                }
            }
        }
        require(destination.isFile && destination.length() > 0L) { "Downloaded file is empty" }
    }

    private fun apiText(
        repo: GitHubRepository,
        path: String,
        token: String?,
    ): String = fetchText("https://api.github.com/repos/${repo.owner}/${repo.name}/$path", token)

    private fun fetchText(
        url: String,
        token: String?,
    ): String {
        val response = execute(url, token, "application/vnd.github+json")
        response.use {
            require(it.isSuccessful) { "GitHub returned HTTP ${it.code}" }
            return it.body?.string() ?: error("GitHub returned an empty response")
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
        token?.trim()?.takeIf(String::isNotBlank)?.let {
            builder.header("Authorization", "Bearer $it")
        }
        return client.newCall(builder.build()).execute()
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
        @param:Json(name = "artifacts_url") val artifactsUrl: String,
        @param:Json(name = "created_at") val createdAt: String? = null,
        @param:Json(name = "updated_at") val updatedAt: String? = null,
    )

    @JsonClass(generateAdapter = true)
    internal data class GitHubArtifactsResponse(
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

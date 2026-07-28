package com.dergoogler.mmrl.debug

import android.content.Context
import com.dergoogler.mmrl.github.GitHubTokenStore
import com.dergoogler.mmrl.network.NetworkUtils
import okhttp3.Request
import java.net.URI

class LsposedRepoDebugProbe(context: Context) {
    private val client by lazy { NetworkUtils.createOkHttpClient() }
    private val tokenStore by lazy { GitHubTokenStore(context) }

    fun endpointMatrixProbe(): DebugProbeResult {
        val results = ENDPOINTS.map(::checkEndpoint)
        val usable = results.firstOrNull { result -> result.statusCode?.let { code -> code in 200..299 } == true && result.jsonLike }
        val forbidden = results.filter { it.statusCode == 403 }
        return DebugProbeResult(
            id = "lsposed-repo-endpoints",
            title = "LSPosed repository endpoint matrix",
            group = DebugProbeGroup.REPOSITORY,
            status = when {
                usable != null -> if (forbidden.isEmpty()) DebugProbeStatus.PASS else DebugProbeStatus.WARN
                forbidden.isNotEmpty() -> DebugProbeStatus.FAIL
                else -> DebugProbeStatus.UNKNOWN
            },
            summary = when {
                usable != null && forbidden.isEmpty() -> "Primary/fallback repository endpoint returned JSON."
                usable != null -> "A fallback endpoint returned JSON after one or more 403 responses."
                forbidden.isNotEmpty() -> "Repository endpoints returned HTTP 403 and no fallback succeeded."
                else -> "Repository endpoint probe could not confirm a usable JSON response."
            },
            evidence = results.flatMap { it.toEvidence() } + listOf(
                DebugEvidence("github token", if (tokenStore.hasToken()) "configured" else "not configured"),
                DebugEvidence("authorization policy", "GitHub token is only attached to github hosts; values are redacted"),
            ),
            remedies = when {
                usable != null -> emptyList()
                forbidden.isNotEmpty() && !tokenStore.hasToken() -> listOf("Save a GitHub API token in Settings > Other and retry the repository update.")
                forbidden.isNotEmpty() -> listOf("Check whether the configured GitHub token is valid or rate-limited.")
                else -> listOf("Check DNS/TLS/network connectivity and rerun the probe.")
            },
        )
    }

    private fun checkEndpoint(url: String): EndpointProbeResult {
        val started = System.currentTimeMillis()
        return runCatching {
            val token = tokenStore.getToken()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "MMRL-Debug-Workbench")
                .applyGithubToken(url, token)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                EndpointProbeResult(
                    url = url,
                    statusCode = response.code,
                    contentType = response.header("Content-Type").orEmpty(),
                    bodyBytes = body.toByteArray().size,
                    jsonLike = body.trimStart().let { it.startsWith("[") || it.startsWith("{") },
                    elapsedMs = System.currentTimeMillis() - started,
                    error = null,
                )
            }
        }.getOrElse { failure ->
            EndpointProbeResult(
                url = url,
                statusCode = null,
                contentType = "",
                bodyBytes = 0,
                jsonLike = false,
                elapsedMs = System.currentTimeMillis() - started,
                error = failure.message ?: failure::class.java.simpleName,
            )
        }
    }

    private fun Request.Builder.applyGithubToken(url: String, token: String?): Request.Builder = apply {
        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
        if (host.contains("github", ignoreCase = true)) {
            token?.trim()?.takeIf(String::isNotBlank)?.let { header("Authorization", "Bearer $it") }
        }
    }

    private data class EndpointProbeResult(
        val url: String,
        val statusCode: Int?,
        val contentType: String,
        val bodyBytes: Int,
        val jsonLike: Boolean,
        val elapsedMs: Long,
        val error: String?,
    ) {
        fun toEvidence(): List<DebugEvidence> = listOf(
            DebugEvidence(url, "status=${statusCode ?: "error"}, json=$jsonLike, bytes=$bodyBytes, elapsed=${elapsedMs}ms"),
            DebugEvidence("$url content", if (error == null) contentType.ifBlank { "unknown" } else "error=$error"),
        )
    }

    private companion object {
        val ENDPOINTS = listOf(
            "https://modules.lsposed.org/modules.json",
            "https://backup.modules.lsposed.org/modules.json",
            "https://cdn.jsdelivr.net/gh/Xposed-Modules-Repo/modules@gh-pages/modules.json",
        )
    }
}

package com.dergoogler.mmrl.github

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private val GitHubSourceMode.queryValue: String
    get() =
        when (this) {
            GitHubSourceMode.RELEASE -> "release"
            GitHubSourceMode.NIGHTLY -> "nightly"
        }

data class GitHubSourceSpec(
    val owner: String,
    val repository: String,
    val mode: GitHubSourceMode,
    val includePreReleases: Boolean = false,
    val regex: String = "",
    val assetRegex: String = "",
    val artifactRegex: String = "",
    val rejectRegex: String = "",
    val preferredVariantRegex: String = "",
    val branchRegex: String = "",
    val workflowRegex: String = "",
    val artifactStrategy: GitHubArtifactStrategy = GitHubArtifactStrategy.AUTO,
) {
    val repoUrl: String get() = "https://github.com/$owner/$repository"

    val sourceUrl: String
        get() =
            "$repoUrl?" + buildList {
                add("mmrlSource=${mode.queryValue}")
                if (mode == GitHubSourceMode.RELEASE && includePreReleases) add("includePreReleases=true")
                addEncoded("regex", regex)
                addEncoded("assetRegex", assetRegex)
                addEncoded("artifactRegex", artifactRegex)
                addEncoded("rejectRegex", rejectRegex)
                addEncoded("preferredVariantRegex", preferredVariantRegex)
                addEncoded("branchRegex", branchRegex)
                addEncoded("workflowRegex", workflowRegex)
                if (artifactStrategy != GitHubArtifactStrategy.AUTO) {
                    add("artifactStrategy=${artifactStrategy.queryValue}")
                }
            }.joinToString("&")

    fun withMode(nextMode: GitHubSourceMode): GitHubSourceSpec = copy(mode = nextMode)

    companion object {
        fun fromDownloadUrl(url: String?): GitHubSourceSpec? {
            if (url.isNullOrBlank()) return null
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            val parts = uri.path.trim('/').split('/').filter(String::isNotBlank)

            if (uri.host.equals("api.github.com", ignoreCase = true) &&
                parts.size >= 5 &&
                parts[0] == "repos"
            ) {
                val mode =
                    when {
                        parts.drop(3).take(2) == listOf("actions", "artifacts") -> GitHubSourceMode.NIGHTLY
                        parts.drop(3).take(2) == listOf("releases", "assets") -> GitHubSourceMode.RELEASE
                        else -> return null
                    }
                return GitHubSourceSpec(parts[1], parts[2], mode)
            }

            if (uri.host.equals("github.com", ignoreCase = true) && parts.size >= 4) {
                val mode =
                    when {
                        parts.drop(2).take(2) == listOf("releases", "download") -> GitHubSourceMode.RELEASE
                        else -> return null
                    }
                return GitHubSourceSpec(parts[0], parts[1], mode)
            }

            return null
        }

        fun fromSourceUrl(url: String?): GitHubSourceSpec? {
            if (url.isNullOrBlank()) return null
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            if (!uri.host.equals("github.com", ignoreCase = true)) return null
            val parts = uri.path.trim('/').split('/').filter(String::isNotBlank)
            if (parts.size < 2) return null
            val parameters = parseQuery(uri.rawQuery)
            val mode =
                when (parameters["mmrlSource"]?.lowercase(Locale.ROOT)) {
                    "nightly", "nightlylink", "nightly_link", "nightly-link" -> GitHubSourceMode.NIGHTLY
                    else -> GitHubSourceMode.RELEASE
                }
            return GitHubSourceSpec(
                owner = parts[0],
                repository = parts[1],
                mode = mode,
                includePreReleases = parameters["includePreReleases"].equals("true", ignoreCase = true),
                regex = parameters["regex"].orEmpty(),
                assetRegex = parameters["assetRegex"].orEmpty(),
                artifactRegex = parameters["artifactRegex"].orEmpty(),
                rejectRegex = parameters["rejectRegex"].orEmpty(),
                preferredVariantRegex = parameters["preferredVariantRegex"].orEmpty(),
                branchRegex = parameters["branchRegex"].orEmpty(),
                workflowRegex = parameters["workflowRegex"].orEmpty(),
                artifactStrategy = GitHubArtifactStrategy.fromQuery(parameters["artifactStrategy"]),
            )
        }

        internal fun parseQuery(rawQuery: String?): Map<String, String> =
            rawQuery
                ?.split('&')
                .orEmpty()
                .mapNotNull { item ->
                    val key = item.substringBefore('=', "").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val value = item.substringAfter('=', "")
                    decode(key) to decode(value)
                }.toMap()

        private fun decode(value: String): String =
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}

private fun MutableList<String>.addEncoded(key: String, value: String) {
    value.trim().takeIf(String::isNotBlank)?.let {
        add("$key=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}")
    }
}

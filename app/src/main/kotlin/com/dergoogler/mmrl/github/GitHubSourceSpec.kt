package com.dergoogler.mmrl.github

import java.net.URI
import java.util.Locale

data class GitHubSourceSpec(
    val owner: String,
    val repository: String,
    val mode: GitHubSourceMode,
) {
    val repoUrl: String get() = "https://github.com/$owner/$repository"

    val sourceUrl: String
        get() = "$repoUrl?mmrlSource=${if (mode == GitHubSourceMode.NIGHTLY) "nightly" else "release"}"

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
            val mode =
                uri.rawQuery
                    ?.split('&')
                    .orEmpty()
                    .firstOrNull { it.substringBefore('=').equals("mmrlSource", ignoreCase = true) }
                    ?.substringAfter('=', "")
                    ?.lowercase(Locale.ROOT)
                    ?.let {
                        if (it == "nightly") GitHubSourceMode.NIGHTLY else GitHubSourceMode.RELEASE
                    } ?: GitHubSourceMode.RELEASE
            return GitHubSourceSpec(parts[0], parts[1], mode)
        }
    }
}

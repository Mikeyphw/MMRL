package com.dergoogler.mmrl.github

import java.net.URI
import java.util.Locale

internal object GitHubArtifactArchivePolicy {
    fun isActionsArtifactArchive(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host.orEmpty()
        val parts = uri.path.trim('/').split('/').filter(String::isNotBlank)
        if (host.equals("api.github.com", ignoreCase = true)) {
            return parts.size >= 7 &&
                parts[0] == "repos" &&
                parts[3] == "actions" &&
                parts[4] == "artifacts" &&
                parts.last().equals("zip", ignoreCase = true)
        }
        if (host.equals("nightly.link", ignoreCase = true)) {
            return parts.size >= 5 &&
                (
                    parts.getOrNull(2) == "workflows" ||
                        parts.getOrNull(2) == "actions" ||
                        parts.getOrNull(2) == "suites"
                ) &&
                uri.path.endsWith(".zip", ignoreCase = true)
        }
        return false
    }

    fun moduleRoot(entryNames: List<String>): String? {
        val normalized =
            entryNames
                .asSequence()
                .map { it.trim().trimStart('/') }
                .filter { it.isNotBlank() }
                .filterNot { it.startsWith("__MACOSX/", ignoreCase = true) }
                .toList()

        if (normalized.any { it.equals("module.prop", ignoreCase = true) }) return ""

        return normalized
            .mapNotNull { name ->
                val lower = name.lowercase(Locale.ROOT)
                when {
                    lower.endsWith("/module.prop") -> name.dropLast("/module.prop".length)
                    else -> null
                }
            }.filter(String::isNotBlank)
            .distinct()
            .maxByOrNull { root ->
                normalized.count { it == root || it.startsWith("$root/") }
            }?.let { root -> "$root/" }
    }

    fun downloadFailureMessage(
        url: String,
        code: Int,
        hasToken: Boolean,
        bodySnippet: String?,
    ): String {
        val isArtifact = isActionsArtifactArchive(url)
        val isNightlyLink = runCatching { URI(url).host.equals("nightly.link", ignoreCase = true) }.getOrDefault(false)
        val detail = bodySnippet?.trim()?.takeIf(String::isNotBlank)?.take(220)
        val base =
            when {
                isNightlyLink -> "HTTP $code while downloading nightly.link artifact"
                isArtifact -> "HTTP $code while downloading GitHub Actions artifact"
                else -> "HTTP $code while downloading GitHub file"
            }
        val guidance =
            when {
                isNightlyLink && code == 404 ->
                    "The latest successful artifact may not exist for that workflow, branch, or artifact name. Refresh the source or adjust the regex."
                isNightlyLink ->
                    "Refresh the nightly.link source, check the artifact regex, then retry."
                isArtifact && code in setOf(401, 403) && !hasToken ->
                    "Add a GitHub token with Actions read access, or switch this module source to nightly.link."
                isArtifact && code in setOf(401, 403) ->
                    "Check that the saved GitHub token can read Actions artifacts for this repository, or switch this module source to nightly.link."
                isArtifact && code == 404 ->
                    "The nightly artifact may have expired or been deleted. Refresh the source and retry."
                isArtifact ->
                    "Refresh the nightly source, check the artifact regex, then retry."
                else ->
                    "Retry the download or check the source URL."
            }
        return buildString {
            append(base)
            append(". ")
            append(guidance)
            if (detail != null) {
                append(" Server said: ")
                append(detail)
            }
        }
    }
}

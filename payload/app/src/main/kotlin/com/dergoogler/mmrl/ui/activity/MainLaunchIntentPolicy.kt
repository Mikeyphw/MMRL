package com.dergoogler.mmrl.ui.activity

import android.content.Intent
import android.net.Uri
import java.net.IDN
import java.net.URI
import java.util.Locale
import com.dergoogler.mmrl.repository.normalizeRepositoryUrlInput

object MainLaunchIntentPolicy {
    data class RepositoryProposal(
        val normalizedUrl: String,
        val normalizedHost: String,
        val source: String = "deep-link",
    )

    data class LaunchRequest(
        val openActivity: Boolean = false,
        val openUpdates: Boolean = false,
        val repositoryProposal: RepositoryProposal? = null,
    )

    fun consume(intent: Intent?): LaunchRequest {
        if (intent == null) return LaunchRequest()
        val request = LaunchRequest(
            openActivity = intent.getBooleanExtra(MainActivity.EXTRA_OPEN_ACTIVITY, false),
            openUpdates = intent.getBooleanExtra(MainActivity.EXTRA_OPEN_UPDATES, false),
            repositoryProposal = validatedRepositoryProposal(intent.data),
        )
        intent.removeExtra(MainActivity.EXTRA_OPEN_ACTIVITY)
        intent.removeExtra(MainActivity.EXTRA_OPEN_UPDATES)
        intent.data = null
        return request
    }

    private fun validatedRepositoryProposal(uri: Uri?): RepositoryProposal? {
        uri ?: return null
        val scheme = uri.scheme.orEmpty().lowercase(Locale.ROOT)
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        val allowedContainer =
            (scheme == "mmrl-mikeyphw" && host == "repository") ||
                (scheme == "https" && host == "mmrl.dev" && isRepositoryDeepLinkPath(uri.path.orEmpty()))
        if (!allowedContainer) return null
        val raw = uri.getQueryParameter("url")
            ?: uri.getQueryParameter("repo")
            ?: uri.getQueryParameter("repository")
            ?: return null
        return sanitizeRepositoryProposal(raw)
    }

    private fun isRepositoryDeepLinkPath(path: String): Boolean =
        path == "/repository" || path.startsWith("/repository/")

    private fun sanitizeRepositoryProposal(raw: String): RepositoryProposal? = runCatching {
        val parsed = URI(raw.trim())
        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
        require(scheme == "https") { "Repository deep link must use HTTPS" }
        require(parsed.userInfo == null) { "Repository deep link must not contain credentials" }
        require(parsed.fragment == null) { "Repository deep link must not contain a fragment" }
        val host = parsed.host?.let { IDN.toASCII(it).lowercase(Locale.ROOT) }.orEmpty()
        require(host.isNotBlank()) { "Repository deep link must include a host" }
        val normalized = normalizeRepositoryUrlInput(parsed.toASCIIString())
        val normalizedHost = URI(normalized).host?.let { IDN.toASCII(it).lowercase(Locale.ROOT) }.orEmpty()
        require(normalizedHost.isNotBlank()) { "Repository deep link must include a host" }
        RepositoryProposal(normalizedUrl = normalized, normalizedHost = normalizedHost)
    }.getOrNull()
}

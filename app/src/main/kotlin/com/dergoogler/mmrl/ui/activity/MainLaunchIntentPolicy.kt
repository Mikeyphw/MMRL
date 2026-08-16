package com.dergoogler.mmrl.ui.activity

import android.content.Intent
import android.net.Uri
import java.net.IDN
import java.net.URI
import java.util.Locale

object MainLaunchIntentPolicy {
    data class LaunchRequest(
        val openActivity: Boolean = false,
        val openUpdates: Boolean = false,
        val openRecovery: Boolean = false,
        val repositoryUrl: String? = null,
    )

    fun consume(intent: Intent?): LaunchRequest {
        if (intent == null) return LaunchRequest()
        val request = LaunchRequest(
            openActivity = intent.getBooleanExtra(MainActivity.EXTRA_OPEN_ACTIVITY, false),
            openUpdates = intent.getBooleanExtra(MainActivity.EXTRA_OPEN_UPDATES, false),
            openRecovery = intent.getBooleanExtra(MainActivity.EXTRA_OPEN_RECOVERY, false),
            repositoryUrl = validatedRepositoryUrl(intent.data),
        )
        intent.removeExtra(MainActivity.EXTRA_OPEN_ACTIVITY)
        intent.removeExtra(MainActivity.EXTRA_OPEN_UPDATES)
        intent.removeExtra(MainActivity.EXTRA_OPEN_RECOVERY)
        intent.data = null
        return request
    }

    private fun validatedRepositoryUrl(uri: Uri?): String? {
        uri ?: return null
        val scheme = uri.scheme.orEmpty().lowercase(Locale.ROOT)
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        val allowedContainer =
            (scheme == "mmrl-mikeyphw" && host == "repository") ||
                (scheme == "https" && host == "mmrl.dev" && uri.path.orEmpty().startsWith("/repository"))
        if (!allowedContainer) return null
        val raw = uri.getQueryParameter("url")
            ?: uri.getQueryParameter("repo")
            ?: uri.getQueryParameter("repository")
            ?: return null
        return sanitizeRepositoryUrl(raw)
    }

    private fun sanitizeRepositoryUrl(raw: String): String? = runCatching {
        val parsed = URI(raw.trim())
        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
        require(scheme == "https") { "Repository deep link must use HTTPS" }
        require(parsed.userInfo == null) { "Repository deep link must not contain credentials" }
        require(parsed.fragment == null) { "Repository deep link must not contain a fragment" }
        val host = parsed.host?.let { IDN.toASCII(it).lowercase(Locale.ROOT) }.orEmpty()
        require(host.isNotBlank()) { "Repository deep link must include a host" }
        parsed.toString()
    }.getOrNull()
}

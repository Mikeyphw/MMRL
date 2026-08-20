package com.dergoogler.mmrl.pathHandler

import java.net.IDN
import java.net.InetAddress
import java.net.URI
import java.util.Locale

object WebUiContentPolicy {
    private const val MAX_README_BYTES = 512L * 1024L
    private val allowedSchemes = setOf("https")
    private val allowedHosts = setOf("github.com", "raw.githubusercontent.com", "gitlab.com", "codeberg.org", "mmrl.dev")

    fun requireReadmeUri(raw: String): URI {
        val uri = URI(raw.trim())
        require(uri.scheme?.lowercase(Locale.ROOT) in allowedSchemes) { "README must be loaded over HTTPS" }
        require(uri.userInfo == null) { "README URL must not contain credentials" }
        require(uri.fragment == null) { "README URL must not contain a fragment" }
        val host = uri.host?.let { IDN.toASCII(it).lowercase(Locale.ROOT) }.orEmpty()
        require(host.isNotBlank()) { "README URL must have a host" }
        require(isTrustedHost(host)) { "README host is not trusted" }
        require(!isPrivateAddress(host)) { "README host resolves to a private address" }
        return uri
    }

    fun boundedReadLimitBytes(): Long = MAX_README_BYTES

    fun sanitizeMarkdown(raw: String): String = raw
        .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), "")
        .replace(Regex("(?is)<iframe\\b[^>]*>.*?</iframe>"), "")
        .replace(Regex("(?is)<object\\b[^>]*>.*?</object>"), "")
        .replace(Regex("(?is)<embed\\b[^>]*>.*?</embed>"), "")
        .replace(Regex("(?i)on[a-z]+\\s*="), "data-mmrl-blocked=")
        .replace(Regex("(?i)javascript:"), "blocked-scheme:")

    fun shouldOverrideNavigation(url: String): Boolean = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        if (scheme == "mmrl-mikeyphw") return@runCatching false
        if (scheme != "https") return@runCatching true
        uri.userInfo != null || uri.fragment != null || !isTrustedHost(uri.host.orEmpty())
    }.getOrDefault(true)

    private fun isTrustedHost(rawHost: String): Boolean {
        val host = rawHost.takeIf(String::isNotBlank)
            ?.let { IDN.toASCII(it).lowercase(Locale.ROOT) }
            ?: return false
        return host in allowedHosts || allowedHosts.any { host.endsWith(".$it") }
    }

    private fun isPrivateAddress(host: String): Boolean = runCatching {
        InetAddress.getAllByName(host).any { address ->
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
        }
    }.getOrDefault(true)
}

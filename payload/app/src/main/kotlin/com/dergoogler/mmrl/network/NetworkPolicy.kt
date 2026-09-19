package com.dergoogler.mmrl.network

import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import java.net.InetAddress
import java.net.URI
import java.util.Locale

object NetworkPolicy {
    const val MAX_REPOSITORY_JSON_BYTES: Long = 4L * 1024L * 1024L
    const val MAX_GITHUB_JSON_BYTES: Long = 2L * 1024L * 1024L
    const val MAX_REPOSITORY_MODULES: Int = 5_000
    const val MAX_KERNELSU_CATALOG_ENTRIES: Int = 2_000
    const val MAX_GITHUB_API_PAGES: Int = 5
    const val MAX_HTTP_ERROR_SNIPPET: Int = 500
    const val MAX_DOWNLOAD_BYTES: Long = 1_073_741_824L
    const val MIN_REFRESH_INTERVAL_HOURS: Long = 1L
    const val MAX_REFRESH_INTERVAL_HOURS: Long = 24L * 14L

    private val githubTokenHosts = setOf(
        "api.github.com",
        "github.com",
        "raw.githubusercontent.com",
        "objects.githubusercontent.com",
        "uploads.github.com",
    )

    fun shouldAttachGitHubToken(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return host in githubTokenHosts
    }


    fun requireSafeRemoteUrl(url: String): String {
        val uri = runCatching { URI(url) }.getOrElse { throw IllegalArgumentException("Invalid remote URL", it) }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Remote URL must use HTTPS" }
        require(uri.userInfo == null && uri.fragment == null) { "Remote URL must not contain credentials or fragments" }
        val host = uri.host?.trim()?.lowercase(Locale.ROOT).orEmpty()
        require(host.isNotBlank()) { "Remote URL must include a host" }
        require(host != "localhost" && !host.endsWith(".localhost")) { "Loopback hosts are not allowed" }
        parseIpLiteral(host)?.let { requireSafeRemoteAddress(it) }
        return uri.normalize().toASCIIString()
    }

    fun requireSafeRedirect(fromUrl: String, location: String): String {
        val resolved = runCatching { URI(fromUrl).resolve(location).toString() }
            .getOrElse { throw IllegalArgumentException("Invalid redirect destination", it) }
        return requireSafeRemoteUrl(resolved)
    }

    fun requireSafeRemoteAddress(address: InetAddress): InetAddress {
        require(!address.isAnyLocalAddress) { "Unspecified addresses are not allowed" }
        require(!address.isLoopbackAddress) { "Loopback addresses are not allowed" }
        require(!address.isLinkLocalAddress) { "Link-local addresses are not allowed" }
        require(!address.isSiteLocalAddress) { "Private network addresses are not allowed" }
        require(!address.isMulticastAddress) { "Multicast addresses are not allowed" }
        val bytes = address.address
        if (bytes.size == 4) {
            val a = bytes[0].toInt() and 0xff
            val b = bytes[1].toInt() and 0xff
            // RFC 6598 shared address space is not globally routable and must not be reachable by remote fetches.
            require(!(a == 100 && b in 64..127)) { "Shared/private network addresses are not allowed" }
            // Reject non-routable/reserved destinations that InetAddress does not classify as site-local.
            require(a != 0) { "Unspecified/reserved addresses are not allowed" }
        }
        if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xff
            require(first != 0xfc && first != 0xfd) { "Unique-local IPv6 addresses are not allowed" }
        }
        return address
    }

    private fun parseIpLiteral(host: String): InetAddress? {
        val candidate = host.removePrefix("[").removeSuffix("]")
        val looksLikeIpv4 = candidate.matches(Regex("^[0-9.]+$")) && candidate.count { it == '.' } == 3
        val looksLikeIpv6 = ':' in candidate
        if (!looksLikeIpv4 && !looksLikeIpv6) return null
        return runCatching { InetAddress.getByName(candidate) }.getOrNull()
    }

    fun clampRefreshIntervalHours(value: Long): Long =
        value.coerceIn(MIN_REFRESH_INTERVAL_HOURS, MAX_REFRESH_INTERVAL_HOURS)

    fun sanitizeErrorBody(text: String?): String {
        val clean = text.orEmpty().trim()
        if (clean.isBlank()) return ""
        if (isHtml(clean)) return "HTML response body redacted"
        return clean.take(MAX_HTTP_ERROR_SNIPPET)
    }



    fun httpException(response: Response): NetworkHttpException =
        NetworkHttpException(
            statusCode = response.code,
            requestUrl = response.request.url.toString(),
            responseSnippet = readErrorSnippet(response.body),
        )

    fun requireSuccessful(response: Response) {
        if (!response.isSuccessful) throw httpException(response)
    }

    /** Negative lengths mean unknown and are allowed only because streaming is still hard-bounded. */
    fun declaredDownloadLengthAllowed(contentLength: Long): Boolean =
        contentLength < 0L || contentLength in 0L..MAX_DOWNLOAD_BYTES

    fun addReceivedBytes(received: Long, emitted: Int): Long {
        require(received >= 0L && emitted >= 0) { "Download byte counts must be non-negative" }
        val next = runCatching { Math.addExact(received, emitted.toLong()) }
            .getOrElse { throw IllegalArgumentException("Download byte count overflow", it) }
        require(next <= MAX_DOWNLOAD_BYTES) { "Download exceeds the temporary-file safety limit" }
        return next
    }

    fun readErrorSnippet(body: ResponseBody?): String {
        if (body == null) return ""
        return runCatching {
            val source = body.source()
            source.request((MAX_HTTP_ERROR_SNIPPET + 1).toLong())
            sanitizeErrorBody(source.buffer.clone().readString(Charsets.UTF_8))
        }.getOrDefault("")
    }

    fun readUtf8Bounded(
        body: ResponseBody,
        maxBytes: Long,
        label: String,
    ): String {
        val source = body.source()
        val buffer = Buffer()
        var total = 0L
        while (true) {
            val read = source.read(buffer, DEFAULT_BUFFER_SIZE.toLong())
            if (read == -1L) break
            total += read
            require(total <= maxBytes) {
                "$label exceeds the maximum supported response size ($maxBytes bytes)"
            }
        }
        return buffer.readString(Charsets.UTF_8)
    }

    private fun isHtml(text: String) =
        "<html\\s*>|<head\\s*>|<body\\s*>|<!doctype\\s*html\\s*>"
            .toRegex(RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
}

class NetworkHttpException(
    val statusCode: Int,
    val requestUrl: String?,
    val responseSnippet: String,
) : RuntimeException(
    buildString {
        append("HTTP ").append(statusCode)
        requestUrl?.takeIf(String::isNotBlank)?.let { append(" for ").append(it) }
        responseSnippet.takeIf(String::isNotBlank)?.let { append(": ").append(it) }
    },
)

package com.dergoogler.mmrl.network

import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
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

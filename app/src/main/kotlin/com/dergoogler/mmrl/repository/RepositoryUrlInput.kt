package com.dergoogler.mmrl.repository

import java.net.URI
import java.util.Locale

/** Normalizes repository input while preserving direct JSON paths such as KernelSU Next manifests. */
internal fun normalizeRepositoryUrlInput(input: String): String {
    val value = input.trim()
    require(value.isNotEmpty()) { "Enter a repository URL" }
    require(!value.startsWith("http://", ignoreCase = true)) {
        "Repository URLs must use HTTPS"
    }

    val candidate =
        when {
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("//") -> "https:$value"
            else -> "https://$value"
        }

    val uri = runCatching { URI(candidate) }.getOrElse { error("Invalid repository URL") }
    require(uri.scheme.equals("https", ignoreCase = true)) { "Repository URLs must use HTTPS" }
    require(!uri.host.isNullOrBlank()) { "Invalid repository URL" }
    require(uri.userInfo == null) { "Repository URLs cannot contain credentials" }
    require(uri.fragment == null) { "Repository URLs cannot contain fragments" }

    val host = uri.host.lowercase(Locale.ROOT)
    val port = uri.port.takeIf { it != -1 }?.let { ":$it" }.orEmpty()
    val path = uri.rawPath.orEmpty().ifEmpty { "/" }.replace(Regex("/{2,}"), "/")

    return buildString {
        append("https://")
        append(host)
        append(port)
        append(path)
        uri.rawQuery?.let {
            append('?')
            append(it)
        }
    }
}

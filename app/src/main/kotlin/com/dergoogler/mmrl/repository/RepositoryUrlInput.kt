package com.dergoogler.mmrl.repository

import java.net.URI

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

    return buildString {
        append("https://")
        append(uri.rawAuthority)
        append(uri.rawPath.orEmpty().ifEmpty { "/" })
        uri.rawQuery?.let {
            append('?')
            append(it)
        }
    }
}

package com.dergoogler.mmrl.tasker

import java.net.URI

/**
 * Pure validation shared by Tasker configuration/runtime code.
 * Keeping it Android-free makes the automation boundary easy to unit test.
 */
internal object TaskerAutomationPolicy {
    fun requireSupportedDownloadUrl(value: String): String {
        val trimmed = value.trim()
        val uri = runCatching { URI(trimmed) }
            .getOrElse { throw IllegalArgumentException("Invalid download URL") }
        require(uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) {
            "Only HTTP and HTTPS downloads are supported"
        }
        require(!uri.host.isNullOrBlank()) { "Download URL must include a host" }
        return trimmed
    }

    fun requireSafeModuleId(value: String): String {
        val clean = value.trim()
        require(clean.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid module ID" }
        return clean
    }

    fun sanitizeFilename(value: String): String {
        val clean = value
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
        require(clean.isNotBlank() && clean.any { it != '.' }) { "Invalid filename" }
        return if (clean.endsWith(".zip", ignoreCase = true)) clean else "$clean.zip"
    }
}

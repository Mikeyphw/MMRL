package com.dergoogler.mmrl.debug

import android.content.Context
import com.dergoogler.mmrl.github.GitHubTokenStore

class GitHubTokenDebugProbe(context: Context) {
    private val tokenStore = GitHubTokenStore(context)

    fun run(): DebugProbeResult {
        val hasCipherText = runCatching { tokenStore.hasToken() }.getOrDefault(false)
        val token = runCatching { tokenStore.getToken() }.getOrNull()
        val decrypted = !token.isNullOrBlank()
        return DebugProbeResult(
            id = "github-token-store",
            title = "GitHub API token store",
            group = DebugProbeGroup.SECURITY,
            status = when {
                decrypted -> DebugProbeStatus.PASS
                hasCipherText -> DebugProbeStatus.WARN
                else -> DebugProbeStatus.SKIPPED
            },
            summary = when {
                decrypted -> "GitHub token is configured and decryptable. Raw token is not exported."
                hasCipherText -> "Encrypted token record exists, but it could not be decrypted."
                else -> "No app-wide GitHub token is saved."
            },
            evidence = listOf(
                DebugEvidence("encrypted record", hasCipherText.toString()),
                DebugEvidence("decryptable", decrypted.toString()),
                DebugEvidence("token preview", if (decrypted) "configured:${token!!.length} chars" else "none"),
                DebugEvidence("storage", "GitHubTokenStore / AndroidKeyStore"),
            ),
            remedies = if (hasCipherText && !decrypted) {
                listOf("Open Settings > Other > GitHub API token and save the token again.")
            } else {
                emptyList()
            },
        )
    }
}

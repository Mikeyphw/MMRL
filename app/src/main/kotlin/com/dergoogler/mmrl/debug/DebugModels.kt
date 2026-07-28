package com.dergoogler.mmrl.debug

/** Small, serializable debug model used by the read-only Debug Workbench. */
enum class DebugProbeStatus {
    PASS,
    WARN,
    FAIL,
    SKIPPED,
    UNKNOWN,
}

enum class DebugProbeGroup(val displayName: String) {
    CORE("Core"),
    LSPOSED("LSPosed / Vector"),
    REPOSITORY("Repository"),
    ASH_REXCUE("AshReXcue"),
    SECURITY("Security"),
}

data class DebugEvidence(
    val label: String,
    val value: String,
)

data class DebugProbeResult(
    val id: String,
    val title: String,
    val group: DebugProbeGroup,
    val status: DebugProbeStatus,
    val summary: String,
    val evidence: List<DebugEvidence> = emptyList(),
    val remedies: List<String> = emptyList(),
    val redacted: Boolean = true,
) {
    val healthy: Boolean get() = status == DebugProbeStatus.PASS
}

object DebugRedactor {
    private val bearerRegex = Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+")
    private val githubTokenRegex = Regex("\\b(?:ghp|github_pat|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{12,}\\b")
    private val cookieRegex = Regex("(?i)(cookie\\s*[:=]\\s*)[^\\n;]+")

    fun redact(value: String): String = value
        .replace(bearerRegex) { match -> "${match.groupValues[1]}<redacted>" }
        .replace(githubTokenRegex, "<github-token-redacted>")
        .replace(cookieRegex) { match -> "${match.groupValues[1]}<redacted>" }
}

object DebugReportFormatter {
    fun asText(results: List<DebugProbeResult>): String = buildString {
        appendLine("MMRL Debug Workbench report")
        appendLine("redacted=true")
        results.groupBy { it.group }.forEach { (group, groupResults) ->
            appendLine()
            appendLine("## ${group.displayName}")
            groupResults.forEach { result ->
                appendLine("- [${result.status}] ${result.title}: ${DebugRedactor.redact(result.summary)}")
                result.evidence.forEach { evidence ->
                    appendLine("  - ${evidence.label}: ${DebugRedactor.redact(evidence.value)}")
                }
                result.remedies.forEach { remedy ->
                    appendLine("  - remedy: ${DebugRedactor.redact(remedy)}")
                }
            }
        }
    }
}

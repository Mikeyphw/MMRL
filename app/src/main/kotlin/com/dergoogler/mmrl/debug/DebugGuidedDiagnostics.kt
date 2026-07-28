package com.dergoogler.mmrl.debug

/** Issue-focused views over the read-only Debug Workbench probes. */
enum class DebugIssueFlow(
    val title: String,
    val buttonTitle: String,
    val description: String,
    val probeIds: Set<String>,
) {
    MANAGER_NOT_RECOGNIZED(
        title = "Manager not recognized",
        buttonTitle = "Diagnose manager not recognized",
        description = "Runs the manager visibility and provider fallback probes, then explains which launch path failed.",
        probeIds = setOf("lsposed-manager-packages", "lsposed-provider-modules"),
    ),
    XPOSED_REPO_403(
        title = "Xposed repo 403",
        buttonTitle = "Diagnose Xposed repo 403",
        description = "Runs repository endpoint and token probes, then explains primary, backup, and GitHub-backed fallback status.",
        probeIds = setOf("lsposed-repo-endpoints", "github-token-store"),
    ),
    ASH_REXCUE_NOT_DETECTED(
        title = "AshReXcue not detected",
        buttonTitle = "Diagnose AshReXcue not detected",
        description = "Runs the AshReXcue identity probe and explains folder, id, alias, and staged-module matches.",
        probeIds = setOf("ashrexcue-module-identity"),
    ),
    GITHUB_TOKEN_PROBLEMS(
        title = "GitHub token problems",
        buttonTitle = "Diagnose GitHub token problems",
        description = "Runs the app-wide encrypted token probe and repository matrix without exposing the token value.",
        probeIds = setOf("github-token-store", "lsposed-repo-endpoints"),
    ),
}

data class DebugGuideStep(
    val title: String,
    val status: DebugProbeStatus,
    val summary: String,
    val remedy: String,
)

data class DebugGuideResult(
    val flow: DebugIssueFlow,
    val status: DebugProbeStatus,
    val summary: String,
    val steps: List<DebugGuideStep>,
) {
    val hasProblems: Boolean get() = status == DebugProbeStatus.WARN || status == DebugProbeStatus.FAIL
}

object DebugGuidedDiagnostics {
    fun relevantResults(
        flow: DebugIssueFlow,
        results: List<DebugProbeResult>,
    ): List<DebugProbeResult> = flow.probeIds.mapNotNull { probeId ->
        results.firstOrNull { result -> result.id == probeId }
    }

    fun evaluate(
        flow: DebugIssueFlow,
        results: List<DebugProbeResult>,
    ): DebugGuideResult {
        val relevant = relevantResults(flow, results)
        val resultById = relevant.associateBy { it.id }
        val steps = when (flow) {
            DebugIssueFlow.MANAGER_NOT_RECOGNIZED -> managerSteps(resultById)
            DebugIssueFlow.XPOSED_REPO_403 -> repoSteps(resultById)
            DebugIssueFlow.ASH_REXCUE_NOT_DETECTED -> ashSteps(resultById)
            DebugIssueFlow.GITHUB_TOKEN_PROBLEMS -> tokenSteps(resultById)
        }
        val status = steps.maxByOrNull { it.status.severity }?.status ?: DebugProbeStatus.UNKNOWN
        return DebugGuideResult(
            flow = flow,
            status = status,
            summary = summaryFor(flow, status, steps),
            steps = steps,
        )
    }

    private fun managerSteps(resultById: Map<String, DebugProbeResult>): List<DebugGuideStep> = listOf(
        resultById.step(
            id = "lsposed-manager-packages",
            title = "Manager package visibility",
            missingSummary = "Manager visibility probe was not available in this run.",
            missingRemedy = "Run all probes and include the support bundle so package visibility can be inspected.",
            defaultRemedy = "If Vector Manager is installed but not launchable, share the resolved package and activity rows from the support bundle.",
        ),
        resultById.step(
            id = "lsposed-provider-modules",
            title = "Provider fallback path",
            missingSummary = "Provider module probe was not available in this run.",
            missingRemedy = "Run all probes to inspect /data/adb/modules and /data/adb/modules_update provider folders.",
            defaultRemedy = "If no launchable manager exists, use the guarded provider action bridge or reboot after installing/staging the provider.",
        ),
    )

    private fun repoSteps(resultById: Map<String, DebugProbeResult>): List<DebugGuideStep> = listOf(
        resultById.step(
            id = "lsposed-repo-endpoints",
            title = "Repository endpoint matrix",
            missingSummary = "Repository endpoint probe was not available in this run.",
            missingRemedy = "Run the Xposed repo 403 guided flow again with network available.",
            defaultRemedy = "If the primary endpoint is 403, verify whether backup.modules.lsposed.org or the jsDelivr gh-pages mirror returned JSON.",
        ),
        resultById.step(
            id = "github-token-store",
            title = "App-wide GitHub token",
            missingSummary = "GitHub token probe was not available in this run.",
            missingRemedy = "Open Settings > Other > GitHub API token and save the token again if GitHub-backed fallbacks are rate-limited.",
            defaultRemedy = "If GitHub-backed endpoints are 403 or rate-limited, refresh the encrypted app-wide token and rerun this flow.",
        ),
    )

    private fun ashSteps(resultById: Map<String, DebugProbeResult>): List<DebugGuideStep> = listOf(
        resultById.step(
            id = "ashrexcue-module-identity",
            title = "AshReXcue module identity",
            missingSummary = "AshReXcue identity probe was not available in this run.",
            missingRemedy = "Run the AshReXcue guided flow with root/module paths available.",
            defaultRemedy = "If the module works but is not recognized, share the folder, module.prop id/name, and canonical alias rows.",
        ),
    )

    private fun tokenSteps(resultById: Map<String, DebugProbeResult>): List<DebugGuideStep> = listOf(
        resultById.step(
            id = "github-token-store",
            title = "Encrypted token store",
            missingSummary = "GitHub token probe was not available in this run.",
            missingRemedy = "Open Settings > Other > GitHub API token and save a token before rerunning this flow.",
            defaultRemedy = "If encrypted token exists but cannot decrypt, save the token again so AndroidKeyStore can re-wrap it.",
        ),
        resultById.step(
            id = "lsposed-repo-endpoints",
            title = "Token use in repository requests",
            missingSummary = "Repository endpoint probe was not available in this run.",
            missingRemedy = "Run the repository endpoint matrix to confirm whether GitHub-backed fallbacks are still failing.",
            defaultRemedy = "The token is attached only to GitHub hosts; modules.lsposed.org and backup.modules.lsposed.org do not receive it.",
        ),
    )

    private fun Map<String, DebugProbeResult>.step(
        id: String,
        title: String,
        missingSummary: String,
        missingRemedy: String,
        defaultRemedy: String,
    ): DebugGuideStep {
        val result = get(id) ?: return DebugGuideStep(
            title = title,
            status = DebugProbeStatus.UNKNOWN,
            summary = missingSummary,
            remedy = missingRemedy,
        )
        return DebugGuideStep(
            title = title,
            status = result.status,
            summary = result.summary,
            remedy = result.remedies.firstOrNull() ?: defaultRemedy,
        )
    }

    private fun summaryFor(
        flow: DebugIssueFlow,
        status: DebugProbeStatus,
        steps: List<DebugGuideStep>,
    ): String = when {
        status == DebugProbeStatus.PASS -> "${flow.title}: no blocking problem was detected by the focused probes."
        steps.any { it.status == DebugProbeStatus.FAIL } -> "${flow.title}: at least one focused probe failed. Follow the remedy card before retrying."
        steps.any { it.status == DebugProbeStatus.WARN } -> "${flow.title}: focused probes found a warning that may explain the issue."
        else -> "${flow.title}: focused probes could not produce a definitive result."
    }
}

object DebugGuideFormatter {
    fun asText(guide: DebugGuideResult?): String {
        guide ?: return "No guided diagnostic flow was active when this bundle was exported."
        return buildString {
            appendLine("MMRL Debug Workbench guided diagnostic")
            appendLine("redacted=true")
            appendLine("flow=${guide.flow.name}")
            appendLine("title=${guide.flow.title}")
            appendLine("status=${guide.status}")
            appendLine("summary=${DebugRedactor.redact(guide.summary)}")
            guide.steps.forEach { step ->
                appendLine()
                appendLine("## ${step.title}")
                appendLine("status=${step.status}")
                appendLine("summary=${DebugRedactor.redact(step.summary)}")
                appendLine("remedy=${DebugRedactor.redact(step.remedy)}")
            }
        }
    }

    fun asJson(guide: DebugGuideResult?): String {
        guide ?: return "null"
        return buildString {
            appendLine("{")
            appendLine("  \"redacted\": true,")
            appendLine("  \"flow\": ${guide.flow.name.json()},")
            appendLine("  \"title\": ${guide.flow.title.json()},")
            appendLine("  \"status\": ${guide.status.name.json()},")
            appendLine("  \"summary\": ${DebugRedactor.redact(guide.summary).json()},")
            appendLine("  \"steps\": [")
            guide.steps.forEachIndexed { index, step ->
                appendLine("    {")
                appendLine("      \"title\": ${step.title.json()},")
                appendLine("      \"status\": ${step.status.name.json()},")
                appendLine("      \"summary\": ${DebugRedactor.redact(step.summary).json()},")
                appendLine("      \"remedy\": ${DebugRedactor.redact(step.remedy).json()}")
                appendLine("    }${if (index == guide.steps.lastIndex) "" else ","}")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }

    private fun String.json(): String = buildString {
        append('"')
        this@json.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 32) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0').uppercase())
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }
}

private val DebugProbeStatus.severity: Int
    get() = when (this) {
        DebugProbeStatus.PASS -> 0
        DebugProbeStatus.SKIPPED -> 0
        DebugProbeStatus.UNKNOWN -> 1
        DebugProbeStatus.WARN -> 2
        DebugProbeStatus.FAIL -> 3
    }

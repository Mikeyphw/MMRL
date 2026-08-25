package com.dergoogler.mmrl.ui.screens.settings.debug

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.debug.DebugActionResult
import com.dergoogler.mmrl.debug.DebugActionRunner
import com.dergoogler.mmrl.debug.DebugGuideResult
import com.dergoogler.mmrl.debug.DebugGuidedDiagnostics
import com.dergoogler.mmrl.debug.DebugHistoryComparison
import com.dergoogler.mmrl.debug.DebugHistoryFormatter
import com.dergoogler.mmrl.debug.DebugHistorySnapshot
import com.dergoogler.mmrl.debug.DebugHistoryStore
import com.dergoogler.mmrl.debug.DebugIssueFlow
import com.dergoogler.mmrl.debug.DebugProbeResult
import com.dergoogler.mmrl.debug.DebugProbeRunner
import com.dergoogler.mmrl.debug.DebugProbeStatus
import com.dergoogler.mmrl.debug.DebugReportFormatter
import com.dergoogler.mmrl.debug.DebugSupportBundleExporter
import com.dergoogler.mmrl.ui.component.SettingsScaffold
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.ButtonItem
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.Item
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.Section
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.item.Description
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.item.Title
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Destination<RootGraph>
@Composable
fun DebugWorkbenchScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val runner = remember(context) { DebugProbeRunner(context) }
    val actionRunner = remember(context) { DebugActionRunner(context) }
    val historyStore = remember(context) { DebugHistoryStore(context) }
    val supportBundleExporter = remember(context) { DebugSupportBundleExporter(context) }
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<DebugProbeResult>>(emptyList()) }
    var history by remember { mutableStateOf(historyStore.loadRecent()) }
    var lastComparison by remember { mutableStateOf<DebugHistoryComparison?>(null) }
    var lastReport by remember { mutableStateOf("Run probes to generate a redacted report.") }
    var lastAction by remember { mutableStateOf<DebugActionResult?>(null) }
    var activeGuide by remember { mutableStateOf<DebugGuideResult?>(null) }

    fun runProbeSession(flow: DebugIssueFlow? = null) {
        running = true
        coroutineScope.launch {
            val next = runner.runAll()
            val previous = withContext(Dispatchers.IO) { historyStore.loadRecent().firstOrNull() }
            val comparison = DebugHistoryStore.compare(next, previous)
            val nextHistory = withContext(Dispatchers.IO) {
                historyStore.record(next)
                historyStore.loadRecent()
            }
            results = next
            history = nextHistory
            lastComparison = comparison
            lastReport = DebugReportFormatter.asText(next)
            activeGuide = flow?.let { DebugGuidedDiagnostics.evaluate(it, next) }
            running = false
        }
    }

    SettingsScaffold(
        title = R.string.settings_debug_workbench,
    ) {
        Section {
            ButtonItem(
                enabled = !running,
                onClick = { runProbeSession() },
            ) {
                Title(if (running) "Running probes…" else "Run read-only probes")
                Description("Checks package visibility, Vector/LSPosed providers, Xposed repo fallbacks, GitHub token status. Saves a small redacted local history for comparisons.")
            }

            ButtonItem(
                enabled = results.isNotEmpty(),
                onClick = {
                    coroutineScope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText("MMRL debug report", lastReport)),
                        )
                    }
                },
            ) {
                Title("Copy redacted report")
                Description("Copies a support-safe report. Authorization headers, cookies, and GitHub tokens are redacted.")
            }
        }

        Section(title = "Guided diagnostics") {
            DebugIssueFlow.entries.forEach { flow ->
                ButtonItem(
                    enabled = !running,
                    onClick = { runProbeSession(flow) },
                ) {
                    Title(flow.buttonTitle)
                    Description(flow.description)
                }
            }

            activeGuide?.let { guide ->
                Item {
                    Title("${guide.status.symbol()} ${guide.flow.title}")
                    Description(guide.descriptionText())
                }
            }
        }

        Section(title = "Guarded actions") {
            ButtonItem(
                onClick = { lastAction = actionRunner.openLsposedManager() },
            ) {
                Title("Open resolved manager")
                Description("Uses the same LSPosed/libxposed/Vector manager intent resolution reported by the probes.")
            }

            ButtonItem(
                onClick = { lastAction = actionRunner.runProviderActionBridge() },
            ) {
                Title("Run provider action bridge")
                Description("Runs only the active provider module action.sh selected by the provider refresh plan. No arbitrary shell is exposed.")
            }

            ButtonItem(
                onClick = { lastAction = actionRunner.startRepositoryRefresh() },
            ) {
                Title("Run one-time repository refresh")
                Description("Starts the existing repository foreground service so repo/cache failures are visible in notifications and logs.")
            }

            ButtonItem(
                onClick = { lastAction = actionRunner.stopRepositoryRefresh() },
            ) {
                Title("Stop repository refresh")
                Description("Stops the repository foreground service if it is running.")
            }

            ButtonItem(
                enabled = results.isNotEmpty(),
                onClick = { lastAction = supportBundleExporter.share(results, lastAction, history, activeGuide) },
            ) {
                Title("Share support bundle")
                Description("Exports a ZIP with redacted text, JSON probe reports, bounded redacted history, and the active guided diagnostic flow. Tokens, cookies, and Authorization headers stay redacted.")
            }

            ButtonItem(
                enabled = history.isNotEmpty(),
                onClick = {
                    coroutineScope.launch {
                        val cleared = withContext(Dispatchers.IO) { historyStore.clear() }
                        if (cleared) {
                            history = emptyList()
                            lastComparison = null
                            lastAction = DebugActionResult(
                                status = DebugProbeStatus.PASS,
                                message = "Cleared local Debug Workbench history.",
                            )
                        } else {
                            lastAction = DebugActionResult(
                                status = DebugProbeStatus.WARN,
                                message = "Debug Workbench history could not be cleared.",
                            )
                        }
                    }
                },
            ) {
                Title("Clear local history")
                Description("Deletes only the bounded redacted Debug Workbench history used for comparisons. It does not touch modules, repo cache, scope DBs, or tokens.")
            }

            lastAction?.let { action ->
                Item {
                    Title("Last action: ${action.status.symbol()}")
                    Description(action.message)
                }
            }
        }

        if (lastComparison != null || history.isNotEmpty()) {
            Section(title = "History") {
                Item {
                    Title("Comparison")
                    Description(DebugHistoryFormatter.comparisonText(lastComparison))
                }
                Item {
                    Title("Stored runs: ${history.size}")
                    Description(history.descriptionText())
                }
            }
        }

        if (results.isEmpty()) {
            Section(divider = false) {
                Item {
                    Title("No probe results yet")
                    Description("Run the probes or use a guided diagnostic flow to diagnose manager recognition, provider scan, repo 403 behavior, GitHub token problems.")
                }
            }
        } else {
            results.groupBy { it.group }.forEach { (group, groupResults) ->
                Section(title = group.displayName) {
                    groupResults.forEach { result ->
                        Item {
                            Title("${result.status.symbol()} ${result.title}")
                            Description(result.descriptionText())
                        }
                    }
                }
            }
        }
    }
}

private fun DebugProbeStatus.symbol(): String = when (this) {
    DebugProbeStatus.PASS -> "✓"
    DebugProbeStatus.WARN -> "⚠"
    DebugProbeStatus.FAIL -> "✗"
    DebugProbeStatus.SKIPPED -> "•"
    DebugProbeStatus.UNKNOWN -> "?"
}

private fun DebugProbeResult.descriptionText(): String = buildString {
    append(summary)
    evidence.take(6).forEach { evidence ->
        append("\n")
        append(evidence.label)
        append(": ")
        append(evidence.value)
    }
    if (evidence.size > 6) {
        append("\n")
        append("+")
        append(evidence.size - 6)
        append(" more evidence rows in copied report")
    }
    remedies.forEach { remedy ->
        append("\nRemedy: ")
        append(remedy)
    }
}

private fun DebugGuideResult.descriptionText(): String = buildString {
    append(summary)
    steps.forEach { step ->
        append("\n")
        append(step.status.symbol())
        append(" ")
        append(step.title)
        append(": ")
        append(step.summary)
        append("\nRemedy: ")
        append(step.remedy)
    }
}

private fun List<DebugHistorySnapshot>.descriptionText(): String {
    if (isEmpty()) return "No previous probe runs stored."
    return take(3).joinToString("\n") { snapshot ->
        val failing = snapshot.entries.count { it.status == DebugProbeStatus.FAIL || it.status == DebugProbeStatus.WARN }
        "${snapshot.createdAtMillis}: ${snapshot.entries.size} checks, $failing warnings/failures"
    }
}

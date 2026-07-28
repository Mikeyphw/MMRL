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
import com.dergoogler.mmrl.debug.DebugActionResult
import com.dergoogler.mmrl.debug.DebugActionRunner
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
import kotlinx.coroutines.launch

@Destination<RootGraph>
@Composable
fun DebugWorkbenchScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val runner = remember(context) { DebugProbeRunner(context) }
    val actionRunner = remember(context) { DebugActionRunner(context) }
    val supportBundleExporter = remember(context) { DebugSupportBundleExporter(context) }
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<DebugProbeResult>>(emptyList()) }
    var lastReport by remember { mutableStateOf("Run probes to generate a redacted report.") }
    var lastAction by remember { mutableStateOf<DebugActionResult?>(null) }

    SettingsScaffold(
        title = "Debug Workbench",
    ) {
        Section {
            ButtonItem(
                enabled = !running,
                onClick = {
                    running = true
                    coroutineScope.launch {
                        val next = runner.runAll()
                        results = next
                        lastReport = DebugReportFormatter.asText(next)
                        running = false
                    }
                },
            ) {
                Title(if (running) "Running probes…" else "Run read-only probes")
                Description("Checks package visibility, Vector/LSPosed providers, Xposed repo fallbacks, GitHub token status, and AshReXcue identity. No writes are performed.")
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
                Title("Start repository refresh")
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
                onClick = { lastAction = supportBundleExporter.share(results, lastAction) },
            ) {
                Title("Share support bundle")
                Description("Exports a ZIP with redacted text and JSON probe reports for support. Tokens, cookies, and Authorization headers stay redacted.")
            }

            lastAction?.let { action ->
                Item {
                    Title("Last action: ${action.status.symbol()}")
                    Description(action.message)
                }
            }
        }

        if (results.isEmpty()) {
            Section(divider = false) {
                Item {
                    Title("No probe results yet")
                    Description("Run the probes to diagnose manager recognition, provider scan, repo 403 behavior, and AshReXcue detection.")
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

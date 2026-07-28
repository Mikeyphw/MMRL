package com.dergoogler.mmrl.ui.screens.settings.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.dergoogler.mmrl.debug.DebugProbeResult
import com.dergoogler.mmrl.debug.DebugProbeRunner
import com.dergoogler.mmrl.debug.DebugProbeStatus
import com.dergoogler.mmrl.debug.DebugReportFormatter
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
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val runner = remember(context) { DebugProbeRunner(context) }
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<DebugProbeResult>>(emptyList()) }
    var lastReport by remember { mutableStateOf("Run probes to generate a redacted report.") }

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
                onClick = { clipboard.setText(AnnotatedString(lastReport)) },
            ) {
                Title("Copy redacted report")
                Description("Copies a support-safe report. Authorization headers, cookies, and GitHub tokens are redacted.")
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

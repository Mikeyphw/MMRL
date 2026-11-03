package com.dergoogler.mmrl.ui.screens.lsposed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.lsposed.VectorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** No root work on composition. Every inspection and mutation is user initiated. */
@Composable
fun VectorManagerPanel() {
    val context = LocalContext.current.applicationContext
    val coroutineScope = rememberCoroutineScope()
    var overview by remember { mutableStateOf<VectorManager.Overview?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var currentScope by remember { mutableStateOf<List<VectorManager.Scope>?>(null) }
    var target by remember { mutableStateOf("") }
    var moduleQuery by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<VectorManager.Change?>(null) }

    fun refresh() {
        if (busy) return
        busy = true
        error = null
        notice = null
        selected = null
        currentScope = null
        target = ""
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { VectorManager.inspect(context) } }
            result.onSuccess { overview = it }.onFailure {
                overview = null
                error = it.message ?: it.javaClass.simpleName
            }
            busy = false
        }
    }

    fun inspectScope(modulePackage: String) {
        if (busy) return
        busy = true
        error = null
        selected = modulePackage
        currentScope = null
        target = ""
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { VectorManager.scopes(context, modulePackage) }
            }
            result.onSuccess { currentScope = it }.onFailure {
                error = it.message ?: it.javaClass.simpleName
            }
            busy = false
        }
    }

    fun propose(module: VectorManager.Module, action: VectorManager.Action, targetPackage: String? = null) {
        if (busy) return
        // No stale write while a scope read is outstanding; mutations always re-read in backend.
        if (action in setOf(VectorManager.Action.ADD_SCOPE, VectorManager.Action.REMOVE_SCOPE) &&
            (selected != module.packageName || currentScope == null)) return
        pending = VectorManager.Change(module.packageName, action, module.enabled, targetPackage,
            if (selected == module.packageName) currentScope else null)
    }

    pending?.let { change ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.lsposed_manage_confirm_title)) },
            text = {
                Text(stringResource(R.string.lsposed_manage_confirm_detail,
                    change.action.name, change.modulePackage,
                    change.targetPackage ?: stringResource(R.string.lsposed_manage_none)))
            },
            confirmButton = {
                Button(onClick = {
                    pending = null
                    busy = true
                    error = null
                    notice = null
                    coroutineScope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            runCatching { VectorManager.change(context, change) }
                        }
                        outcome.onSuccess {
                            overview = it.overview
                            currentScope = if (selected == change.modulePackage) it.scope else null
                            target = ""
                            notice = it.detail
                        }.onFailure {
                            // Never imply a failed operation rolled itself back.
                            error = (it.message ?: it.javaClass.simpleName) +
                                " — Refresh and verify Vector state before trying again."
                            overview = null
                            currentScope = null
                        }
                        busy = false
                    }
                }) { Text(stringResource(R.string.lsposed_manage_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(stringResource(R.string.lsposed_manage_cancel))
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.lsposed_manage_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.lsposed_manage_disclaimer), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { refresh() }, enabled = !busy) {
            Text(stringResource(R.string.lsposed_manage_refresh))
        }
        if (busy) Text(stringResource(R.string.lsposed_manage_working))
        error?.let { failure ->
            Text(failure, color = MaterialTheme.colorScheme.error)
            if (failure.contains("[BIND_")) {
                Text(stringResource(R.string.lsposed_manage_root_bind_help),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        overview?.let { data ->
            Text(stringResource(R.string.lsposed_manage_framework, data.frameworkVersion, data.apiVersion),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.lsposed_manage_module_count,
                data.modules.count { it.enabled }, data.modules.size),
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = moduleQuery,
                onValueChange = { moduleQuery = it.take(80) },
                label = { Text(stringResource(R.string.lsposed_manage_search_modules)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            val term = moduleQuery.trim()
            val visibleModules = data.modules.filter { module ->
                term.isEmpty() || module.label.contains(term, ignoreCase = true) ||
                    module.packageName.contains(term, ignoreCase = true)
            }
            if (data.modules.isEmpty()) Text(stringResource(R.string.lsposed_manage_empty))
            else if (visibleModules.isEmpty()) Text(stringResource(R.string.lsposed_manage_no_matches))
            visibleModules.forEach { module ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(module.label, style = MaterialTheme.typography.titleSmall)
                        Text("${module.packageName} • ${module.format}", style = MaterialTheme.typography.bodySmall)
                        Text(if (module.enabled) stringResource(R.string.lsposed_manage_enabled)
                            else stringResource(R.string.lsposed_manage_disabled))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !busy, onClick = {
                                propose(module, if (module.enabled) VectorManager.Action.DISABLE
                                else VectorManager.Action.ENABLE)
                            }) {
                                Text(if (module.enabled) stringResource(R.string.lsposed_manage_disable)
                                    else stringResource(R.string.lsposed_manage_enable))
                            }
                            OutlinedButton(enabled = !busy, onClick = {
                                if (selected == module.packageName) {
                                    selected = null
                                    currentScope = null
                                    target = ""
                                } else inspectScope(module.packageName)
                            }) {
                                Text(stringResource(R.string.lsposed_manage_scopes))
                            }
                        }
                        if (selected == module.packageName) {
                            currentScope?.let { entries ->
                                if (entries.isEmpty()) Text(stringResource(R.string.lsposed_manage_no_scope))
                                entries.forEach { entry ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("${entry.packageName}/${entry.userId}",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall)
                                        if (entry.userId == 0 && entry.packageName !in setOf("android", "system")) {
                                            TextButton(enabled = !busy, onClick = {
                                                propose(module, VectorManager.Action.REMOVE_SCOPE, entry.packageName)
                                            }) { Text(stringResource(R.string.lsposed_manage_remove)) }
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = target, onValueChange = { target = it.take(180) },
                                    label = { Text(stringResource(R.string.lsposed_manage_target)) },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    enabled = !busy,
                                )
                                if (!module.enabled) {
                                    Text(stringResource(R.string.lsposed_manage_scope_add_requires_enable),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(enabled = !busy && module.enabled && target.isNotBlank(), onClick = {
                                    propose(module, VectorManager.Action.ADD_SCOPE, target.trim())
                                }) { Text(stringResource(R.string.lsposed_manage_add)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

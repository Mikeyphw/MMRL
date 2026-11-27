package com.dergoogler.mmrl.ui.screens.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.database.activity.ActivityHistoryRepository
import com.dergoogler.mmrl.ui.component.SettingsScaffold
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.Item
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.item.Title
import com.dergoogler.mmrl.ui.component.listItem.dsl.component.item.Description
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ActivityHistoryViewModel @Inject constructor(private val repo: ActivityHistoryRepository) : ViewModel() {
    private val _pageIndex = MutableStateFlow(0)
    val pageIndex = _pageIndex.asStateFlow()
    val totalCount = repo.totalCount
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val operations = _pageIndex.flatMapLatest { page -> repo.page(page) }
    val diagnostics = repo.diagnostics

    fun older(total: Int) {
        if (total > 0 && (_pageIndex.value + 1L) * 50 < total.toLong()) {
            _pageIndex.value += 1
        }
    }

    fun newer() {
        if (_pageIndex.value > 0) _pageIndex.value -= 1
    }
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun acknowledgeUnknown(id: String) {
        viewModelScope.launch {
            runCatching { repo.acknowledgeUnknown(id) }
                .onFailure { _error.value = "Acknowledgement unavailable; no action retried" }
                .onSuccess { _error.value = null }
        }
    }
}

@Destination<RootGraph>
@Composable
fun ActivityHistoryScreen() {
    val viewModel: ActivityHistoryViewModel = hiltViewModel()
    val operations by viewModel.operations.collectAsStateWithLifecycle(initialValue = emptyList())
    val pageIndex by viewModel.pageIndex.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle(initialValue = 0)
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle(initialValue = emptyList())
    val error by viewModel.error.collectAsStateWithLifecycle()
    SettingsScaffold(title = R.string.activity_history) {
        if (error != null) Item { Description(error!!) }
        if (operations.isEmpty() && totalCount == 0) {
            Item {
                Title(R.string.activity_empty)
                Description(R.string.activity_scope)
            }
        } else {
            operations.forEach { operation ->
                Item {
                    Title("${operation.kind} · ${operation.status}")
                    Description("${operation.phase} · ${operation.progressPercent?.let { "$it%" } ?: "—"}" +
                        (operation.failureCode?.let { " · $it" } ?: "") +
                        (operation.parentId?.let { " · parent $it" } ?: "") +
                        (if (operation.rebootRequired) " · reboot required; configured state only" else "") +
                        diagnostics.filter { it.operationId == operation.id }.take(3)
                            .joinToString("") { " · ${it.code}" })
                }
                if (operation.status == "RECONCILIATION_REQUIRED") {
                    OutlinedButton(onClick = { viewModel.acknowledgeUnknown(operation.id) }) {
                        Text("Acknowledge unknown outcome · no action retried")
                    }
                }
            }
        }
        if (totalCount > 0) {
            Item {
                Title("Activity · page ${pageIndex + 1} of ${(totalCount - 1) / 50 + 1}")
                Description("Historical operations remain stored; viewing does not retry actions")
            }
            if (pageIndex > 0) {
                OutlinedButton(onClick = { viewModel.newer() }) { Text("Newer activity") }
            }
            if ((pageIndex + 1L) * 50 < totalCount.toLong()) {
                OutlinedButton(onClick = { viewModel.older(totalCount) }) { Text("Older activity") }
            }
        }
    }
}

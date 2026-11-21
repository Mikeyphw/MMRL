package com.dergoogler.mmrl.ui.screens.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
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
class ActivityHistoryViewModel @Inject constructor(repo: ActivityHistoryRepository) : ViewModel() {
    val operations = repo.recent
}

@Destination<RootGraph>
@Composable
fun ActivityHistoryScreen() {
    val viewModel: ActivityHistoryViewModel = hiltViewModel()
    val operations by viewModel.operations.collectAsStateWithLifecycle(initialValue = emptyList())
    SettingsScaffold(title = R.string.activity_history) {
        if (operations.isEmpty()) {
            Item {
                Title(R.string.activity_empty)
                Description(R.string.activity_scope)
            }
        } else {
            operations.forEach { operation ->
                Item {
                    Title("${operation.kind} · ${operation.status}")
                    Description("${operation.phase} · ${operation.progressPercent?.let { "$it%" } ?: "—"}" +
                        (operation.failureCode?.let { " · $it" } ?: ""))
                }
            }
        }
    }
}

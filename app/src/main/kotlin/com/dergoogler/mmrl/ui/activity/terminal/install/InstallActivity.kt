package com.dergoogler.mmrl.ui.activity.terminal.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ext.tmpDir
import com.dergoogler.mmrl.ui.activity.TerminalActivity
import com.dergoogler.mmrl.ui.activity.setBaseContent
import com.dergoogler.mmrl.ui.component.dialog.ConfirmDialog
import com.dergoogler.mmrl.viewmodel.InstallViewModel
import dev.dergoogler.mmrl.compat.BuildCompat
import kotlinx.coroutines.launch
import timber.log.Timber

class InstallActivity : TerminalActivity() {
    private var confirmDialog by mutableStateOf(true)
    private val viewModel by viewModels<InstallViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("InstallActivity onCreate")
        super.onCreate(savedInstanceState)

        val uris: ArrayList<Uri>? =
            if (intent.data != null) {
                arrayListOf(intent.data!!)
            } else {
                if (BuildCompat.atLeastT) {
                    intent.getParcelableArrayListExtra("uris", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra("uris")
                }
            }

        Log.d(TAG, "InstallActivity onCreate: $uris")

        val confirm = intent.getBooleanExtra(EXTRA_CONFIRM, true)
        val parentOperationId = intent.getStringExtra(EXTRA_PARENT_OPERATION_ID)
        val rollbackMode = intent.getBooleanExtra(EXTRA_ROLLBACK_MODE, false)

        if (uris.isNullOrEmpty()) {
            finish()
            return
        }

        if (!confirm) {
            initModule(uris.toList(), parentOperationId, rollbackMode)
        }

        setBaseContent {
            if (confirm && confirmDialog) {
                ConfirmDialog(
                    title = R.string.install_screen_confirm_title,
                    description = R.string.install_screen_confirm_text,
                    onClose = {
                        confirmDialog = false
                        finish()
                    },
                    onConfirm = {
                        confirmDialog = false
                        initModule(uris.toList(), parentOperationId, rollbackMode)
                    },
                )
            }

            InstallScreen(viewModel)
        }
    }

    override fun onDestroy() {
        Timber.d("InstallActivity onDestroy")
        tmpDir.deleteRecursively()
        viewModel.destroy()
        super.onDestroy()
    }

    private fun initModule(
        uris: List<Uri>,
        parentOperationId: String?,
        rollbackMode: Boolean,
    ) {
        val job =
            lifecycleScope.launch {
                viewModel.installModules(
                    uris = uris,
                    parentOperationId = parentOperationId,
                    rollbackMode = rollbackMode,
                )
            }

        terminalJob = job
    }

    companion object {
        private const val TAG = "InstallActivity"
        private const val EXTRA_CONFIRM = "confirm"
        private const val EXTRA_PARENT_OPERATION_ID = "parentOperationId"
        private const val EXTRA_ROLLBACK_MODE = "rollbackMode"

        fun start(
            context: Context,
            uri: List<Uri>,
            confirm: Boolean = true,
            parentOperationId: String? = null,
            rollbackMode: Boolean = false,
        ) {
            val intent =
                Intent(context, InstallActivity::class.java)
                    .apply {
                        putExtra(EXTRA_CONFIRM, confirm)
                        putExtra(EXTRA_PARENT_OPERATION_ID, parentOperationId)
                        putExtra(EXTRA_ROLLBACK_MODE, rollbackMode)
                        putParcelableArrayListExtra("uris", ArrayList(uri))
                    }

            context.startActivity(intent)
        }

        fun start(
            context: Context,
            uri: Uri,
            confirm: Boolean = true,
            parentOperationId: String? = null,
            rollbackMode: Boolean = false,
        ) {
            start(context, listOf(uri), confirm, parentOperationId, rollbackMode)
        }
    }
}

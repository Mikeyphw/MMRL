package com.dergoogler.mmrl.ui.activity.terminal.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.ext.tmpDir
import com.dergoogler.mmrl.ui.activity.TerminalActivity
import com.dergoogler.mmrl.ui.activity.terminal.PrivilegedLaunchSessions
import com.dergoogler.mmrl.ui.activity.setBaseContent
import com.dergoogler.mmrl.ui.component.dialog.ConfirmDialog
import com.dergoogler.mmrl.viewmodel.InstallViewModel
import timber.log.Timber

class InstallActivity : TerminalActivity() {
    private val viewModel by viewModels<InstallViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("InstallActivity onCreate")
        super.onCreate(savedInstanceState)

        val request =
            PrivilegedLaunchSessions.getInstall(
                intent.getStringExtra(EXTRA_SESSION_ID),
            )
        if (request == null) {
            Log.w(TAG, "InstallActivity rejected missing/expired launch session")
            finish()
            return
        }

        val uris = request.uris
        Log.d(TAG, "InstallActivity onCreate: ${uris.size} archive(s)")

        val confirm = request.confirm
        val parentOperationId = request.parentOperationId
        val rollbackMode = request.rollbackMode
        val expectedModuleIds = request.expectedModuleIds
        val expectedArchiveSha256 = request.expectedArchiveSha256

        initModule(
            uris = uris.toList(),
            parentOperationId = parentOperationId,
            rollbackMode = rollbackMode,
            expectedModuleIds = expectedModuleIds,
            expectedArchiveSha256 = expectedArchiveSha256,
            requireApproval = confirm,
        )

        setBaseContent {
            viewModel.approvalRequest?.let { approval ->
                ConfirmDialog(
                    title = getString(R.string.install_screen_confirm_title),
                    description = approval.summary,
                    onClose = {
                        viewModel.respondToApproval(false)
                        finish()
                    },
                    onConfirm = { viewModel.respondToApproval(true) },
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
        expectedModuleIds: List<String>,
        expectedArchiveSha256: List<String>,
        requireApproval: Boolean,
    ) {
        viewModel.startInstall(
            uris = uris,
            parentOperationId = parentOperationId,
            rollbackMode = rollbackMode,
            expectedModuleIds = expectedModuleIds,
            expectedArchiveSha256 = expectedArchiveSha256,
            requireApproval = requireApproval,
        )
    }

    companion object {
        private const val TAG = "InstallActivity"
        private const val EXTRA_SESSION_ID = "privilegedLaunchSession"

        fun start(
            context: Context,
            uri: List<Uri>,
            confirm: Boolean = true,
            parentOperationId: String? = null,
            rollbackMode: Boolean = false,
            expectedModuleIds: List<String> = emptyList(),
            expectedArchiveSha256: List<String> = emptyList(),
        ) {
            val sessionId =
                PrivilegedLaunchSessions.createInstall(
                    PrivilegedLaunchSessions.InstallRequest(
                        uris = uri.toList(),
                        confirm = confirm,
                        parentOperationId = parentOperationId,
                        rollbackMode = rollbackMode,
                        expectedModuleIds = expectedModuleIds.toList(),
                        expectedArchiveSha256 = expectedArchiveSha256.toList(),
                    ),
                )
            context.startActivity(
                Intent(context, InstallActivity::class.java)
                    .putExtra(EXTRA_SESSION_ID, sessionId),
            )
        }

        fun start(
            context: Context,
            uri: Uri,
            confirm: Boolean = true,
            parentOperationId: String? = null,
            rollbackMode: Boolean = false,
            expectedModuleId: String? = null,
            expectedArchiveSha256: String? = null,
        ) {
            start(
                context = context,
                uri = listOf(uri),
                confirm = confirm,
                parentOperationId = parentOperationId,
                rollbackMode = rollbackMode,
                expectedModuleIds = expectedModuleId?.let(::listOf).orEmpty(),
                expectedArchiveSha256 = expectedArchiveSha256?.let(::listOf).orEmpty(),
            )
        }

        internal fun startExternalReviewed(
            context: Context,
            uri: Uri,
            grantFlags: Int,
        ) {
            val sessionId =
                PrivilegedLaunchSessions.createInstall(
                    PrivilegedLaunchSessions.InstallRequest(
                        uris = listOf(uri),
                        confirm = true,
                        parentOperationId = null,
                        rollbackMode = false,
                        expectedModuleIds = emptyList(),
                        expectedArchiveSha256 = emptyList(),
                    ),
                )
            context.startActivity(
                Intent(context, InstallActivity::class.java)
                    .setData(uri)
                    .putExtra(EXTRA_SESSION_ID, sessionId)
                    .addFlags(grantFlags),
            )
        }
    }
}

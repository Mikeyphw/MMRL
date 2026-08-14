package com.dergoogler.mmrl.ui.activity.terminal.action

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.ui.activity.TerminalActivity
import com.dergoogler.mmrl.ui.activity.terminal.PrivilegedLaunchSessions
import com.dergoogler.mmrl.ui.activity.setBaseContent
import com.dergoogler.mmrl.viewmodel.ActionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionActivity : TerminalActivity() {
    private val viewModel by viewModels<ActionViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        val request =
            PrivilegedLaunchSessions.getAction(
                intent.getStringExtra(EXTRA_SESSION_ID),
            )
        val modId = request?.moduleId

        if (modId == null) {
            Log.w(TAG, "ActionActivity rejected missing/expired launch session")
            finish()
            return
        }

        Log.d(TAG, "onCreate: $modId")
        initAction(modId)

        setBaseContent {
            ActionScreen(viewModel)
        }
    }

    private fun initAction(modId: ModId) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.runAction(modId)
        }
    }

    override fun onDestroy() {
        viewModel.destroy()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ActionActivity"
        private const val EXTRA_SESSION_ID = "privilegedLaunchSession"

        fun start(
            context: Context,
            modId: ModId,
        ) {
            val sessionId = PrivilegedLaunchSessions.createAction(modId)
            context.startActivity(
                Intent(context, ActionActivity::class.java)
                    .putExtra(EXTRA_SESSION_ID, sessionId),
            )
        }
    }
}

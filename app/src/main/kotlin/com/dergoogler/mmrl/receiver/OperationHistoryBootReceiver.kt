package com.dergoogler.mmrl.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dergoogler.mmrl.repository.OperationHistoryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OperationHistoryBootReceiver : BroadcastReceiver() {
    @Inject lateinit var historyRepository: OperationHistoryRepository

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                historyRepository.recoverAfterBoot()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

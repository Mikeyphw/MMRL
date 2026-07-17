package com.dergoogler.mmrl.ash.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AshAutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CHECK_NOW -> AshAutomationScheduler.enqueueImmediate(
                context = context,
                reason = "notification_action",
            )
            Intent.ACTION_BOOT_COMPLETED -> AshAutomationScheduler.enqueueImmediate(
                context = context,
                reason = "boot_completed",
                delayMinutes = 2,
            )
            Intent.ACTION_MY_PACKAGE_REPLACED -> AshAutomationScheduler.enqueueImmediate(
                context = context,
                reason = "package_replaced",
                delayMinutes = 1,
            )
        }
    }

    companion object {
        const val ACTION_CHECK_NOW = "com.mikeyphw.mmrl.ash.action.CHECK_NOW"
    }
}

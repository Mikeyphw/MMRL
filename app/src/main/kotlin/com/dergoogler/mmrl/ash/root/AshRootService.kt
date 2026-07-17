package com.dergoogler.mmrl.ash.root

import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService

class AshRootService : RootService() {
    private val executor = AshCtlExecutor()

    private val binder = object : IAshReXcueService.Stub() {
        override fun moduleState(): String = executor.moduleState()
        override fun serviceInfo(): String = executor.serviceInfo()
        override fun capabilities(): String = executor.capabilities()
        override fun snapshot(activityLimit: Int): String = executor.snapshot(activityLimit)
        override fun setSetting(key: String, value: String): String = executor.setSetting(key, value)
        override fun setSettings(keys: Array<out String>, values: Array<out String>): String = executor.setSettings(keys, values)
        override fun setTrust(folder: String, trust: String): String = executor.setTrust(folder, trust)
        override fun restoreOne(folder: String): String = executor.restoreOne(folder)
        override fun restoreHalf(): String = executor.restoreHalf()
        override fun restoreAll(): String = executor.restoreAll()
        override fun completeTrial(): String = executor.completeTrial()
        override fun rollbackTrial(): String = executor.rollbackTrial()
        override fun discardPendingSettings(): String = executor.discardPendingSettings()
        override fun exportDiagnostics(): String = executor.exportDiagnostics()
    }

    override fun onBind(intent: Intent): IBinder = binder
}

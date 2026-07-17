package com.dergoogler.mmrl.ash.root

import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService

class AshRootService : RootService() {
    private val executor = AshCtlExecutor()

    private val binder = object : IAshReXcueService.Stub() {
        override fun moduleAvailable(): Boolean = executor.moduleAvailable()
        override fun serviceInfo(): String = executor.serviceInfo()
        override fun status(): String = executor.status()
        override fun modules(): String = executor.modules()
        override fun quarantine(): String = executor.quarantine()
        override fun activity(limit: Int): String = executor.activity(limit)
        override fun settings(): String = executor.settings()
        override fun pendingSettings(): String = executor.pendingSettings()
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

package com.dergoogler.mmrl.debug

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DebugProbeRunner(
    private val context: Context,
    private val activeModuleRoot: File = File("/data/adb/modules"),
    private val stagedModuleRoot: File = File("/data/adb/modules_update"),
) {
    suspend fun runAll(): List<DebugProbeResult> = withContext(Dispatchers.IO) {
        val lsposed = LsposedDebugProbe(context, activeModuleRoot, stagedModuleRoot)
        listOf(
            GitHubTokenDebugProbe(context).run(),
            lsposed.managerProbe(),
            lsposed.providerProbe(),
            LsposedRepoDebugProbe(context).endpointMatrixProbe(),
        )
    }
}

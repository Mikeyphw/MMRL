package com.dergoogler.mmrl.utils

import android.content.Context
import android.content.ServiceConnection
import com.dergoogler.mmrl.platform.Platform
import com.dergoogler.mmrl.platform.Platform.Companion.createPlatformIntent
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.ksu.KsuNative
import com.dergoogler.mmrl.platform.model.IProvider
import com.dergoogler.mmrl.platform.stub.IServiceManager
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LibSuProvider(
    private val context: Context,
    private val platform: Platform,
) : IProvider {
    override val name = "LibSu"

    override fun isAvailable() = true

    override suspend fun isAuthorized() =
        suspendCancellableCoroutine { continuation ->
            Shell.EXECUTOR.execute {
                runCatching {
                    Shell.getShell()
                }.onSuccess { shell ->
                    continuation.resume(shell.isRoot)
                }.onFailure {
                    continuation.resume(false)
                }
            }
        }

    private val serviceIntent
        get() = context.createPlatformIntent<SuService>(platform)

    override fun bind(connection: ServiceConnection) {
        RootService.bind(serviceIntent, connection)
    }

    override fun unbind(connection: ServiceConnection) {
        RootService.unbind(connection)
    }
}

private suspend fun init(
    platform: Platform,
    context: Context,
    self: PlatformManager,
): IServiceManager? {
    val provider = LibSuProvider(context, platform)

    if (platform.isNonRoot) {
        return null
    }

    return self.from(provider)
}

suspend fun initPlatform(
    context: Context,
    platform: Platform,
): Boolean {
    val previous = PlatformManager.preferredPlatform
    PlatformManager.selectPreferred(platform)
    if (previous != platform) PlatformManager.release()
    if (platform.isNonRoot || platform.isUnknown) return false
    val initialized = PlatformManager.init { init(platform, context, this) }
    if (KsuInitializationPolicy.shouldAttemptManagerAuthorization(initialized, PlatformManager.platform)) {
        // Manager authorization must precede every app-side KernelSU capability query.
        KsuNative.becomeManager(context.packageName)
    }
    return initialized
}

suspend fun initPlatform(
    scope: CoroutineScope,
    context: Context,
    platform: Platform,
) = scope.async(Dispatchers.IO) { initPlatform(context, platform) }

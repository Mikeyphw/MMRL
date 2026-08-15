package com.dergoogler.mmrl.tasker

internal object TaskerRootAwaitPolicy {
    const val MODULE_STATE_CALLBACK_TIMEOUT_MS = 30_000L

    fun timeoutMessage(): String =
        "Module state change timed out; outcome unknown. Reconcile module state before retrying."
}

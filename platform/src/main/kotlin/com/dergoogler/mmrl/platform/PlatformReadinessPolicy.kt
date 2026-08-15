package com.dergoogler.mmrl.platform

/** Pure readiness policy shared by the Binder state machine and regression tests. */
internal object PlatformReadinessPolicy {
    fun isReady(authorized: Boolean, binderAlive: Boolean, detected: Platform): Boolean =
        authorized && binderAlive && detected.isPrivilegedRoot
}

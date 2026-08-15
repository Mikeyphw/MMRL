package com.dergoogler.mmrl.platform

internal object BinderLifecyclePolicy {
    enum class AcquireAction { REUSE_LIVE, WAIT_FOR_TRACKED_RECONNECT, BIND_NEW }

    enum class LossReason {
        DISCONNECTED,
        BINDING_DIED,
        BINDER_DIED,
        NULL_BINDING,
        CANCELLED,
    }

    fun shouldReleaseBinding(reason: LossReason): Boolean =
        reason != LossReason.DISCONNECTED

    fun shouldRebind(reason: LossReason): Boolean =
        reason == LossReason.BINDING_DIED || reason == LossReason.BINDER_DIED

    fun acquireAction(hasLiveBinder: Boolean, hasTrackedBinding: Boolean): AcquireAction = when {
        hasLiveBinder -> AcquireAction.REUSE_LIVE
        hasTrackedBinding -> AcquireAction.WAIT_FOR_TRACKED_RECONNECT
        else -> AcquireAction.BIND_NEW
    }

    /** Initial bind callbacks may publish; reconnect callbacks must still belong to the tracked binding. */
    fun canPublish(initial: Boolean, isTrackedConnection: Boolean): Boolean =
        initial || isTrackedConnection
}

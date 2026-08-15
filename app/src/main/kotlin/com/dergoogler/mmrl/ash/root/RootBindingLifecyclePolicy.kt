package com.dergoogler.mmrl.ash.root

/**
 * Android ServiceConnection semantics for the Ash root client. A transient disconnect keeps the
 * framework binding so it can reconnect, but while that binding is tracked the client must not
 * start a second binding in parallel. Dead/null bindings are released.
 */
internal object RootBindingLifecyclePolicy {
    enum class Event { DISCONNECTED, BINDING_DIED, NULL_BINDING }
    enum class AcquireAction { REUSE_LIVE, WAIT_FOR_TRACKED_RECONNECT, BIND_NEW }

    data class Action(
        val clearRemote: Boolean,
        val releaseBinding: Boolean,
    )

    fun action(event: Event): Action = when (event) {
        Event.DISCONNECTED -> Action(clearRemote = true, releaseBinding = false)
        Event.BINDING_DIED,
        Event.NULL_BINDING,
        -> Action(clearRemote = true, releaseBinding = true)
    }

    fun acquireAction(hasLiveRemote: Boolean, hasTrackedBinding: Boolean): AcquireAction = when {
        hasLiveRemote -> AcquireAction.REUSE_LIVE
        hasTrackedBinding -> AcquireAction.WAIT_FOR_TRACKED_RECONNECT
        else -> AcquireAction.BIND_NEW
    }

    /** Initial bind callbacks may publish; reconnect callbacks must still belong to the tracked binding. */
    fun canPublish(initial: Boolean, isTrackedConnection: Boolean): Boolean =
        initial || isTrackedConnection
}

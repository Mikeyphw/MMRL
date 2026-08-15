package com.dergoogler.mmrl.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BinderLifecyclePolicyTest {
    @Test
    fun transientDisconnectKeepsBindingForFrameworkReconnect() {
        assertFalse(BinderLifecyclePolicy.shouldReleaseBinding(BinderLifecyclePolicy.LossReason.DISCONNECTED))
        assertFalse(BinderLifecyclePolicy.shouldRebind(BinderLifecyclePolicy.LossReason.DISCONNECTED))
    }

    @Test
    fun terminalBindingStatesReleaseTrackedBinding() {
        assertTrue(BinderLifecyclePolicy.shouldReleaseBinding(BinderLifecyclePolicy.LossReason.BINDING_DIED))
        assertTrue(BinderLifecyclePolicy.shouldReleaseBinding(BinderLifecyclePolicy.LossReason.BINDER_DIED))
        assertTrue(BinderLifecyclePolicy.shouldReleaseBinding(BinderLifecyclePolicy.LossReason.NULL_BINDING))
        assertTrue(BinderLifecyclePolicy.shouldReleaseBinding(BinderLifecyclePolicy.LossReason.CANCELLED))
    }

    @Test
    fun onlyActualDeathStatesRequestAutomaticRebind() {
        assertTrue(BinderLifecyclePolicy.shouldRebind(BinderLifecyclePolicy.LossReason.BINDING_DIED))
        assertTrue(BinderLifecyclePolicy.shouldRebind(BinderLifecyclePolicy.LossReason.BINDER_DIED))
        assertFalse(BinderLifecyclePolicy.shouldRebind(BinderLifecyclePolicy.LossReason.NULL_BINDING))
        assertFalse(BinderLifecyclePolicy.shouldRebind(BinderLifecyclePolicy.LossReason.CANCELLED))
    }
    @Test
    fun trackedDisconnectedBindingBlocksParallelBindUntilFrameworkReconnectOrRelease() {
        assertTrue(
            BinderLifecyclePolicy.acquireAction(false, true) ==
                BinderLifecyclePolicy.AcquireAction.WAIT_FOR_TRACKED_RECONNECT,
        )
        assertTrue(
            BinderLifecyclePolicy.acquireAction(false, false) ==
                BinderLifecyclePolicy.AcquireAction.BIND_NEW,
        )
        assertTrue(
            BinderLifecyclePolicy.acquireAction(true, true) ==
                BinderLifecyclePolicy.AcquireAction.REUSE_LIVE,
        )
    }

    @Test
    fun initialPublicationDoesNotRequirePreexistingTrackedConnectionButReconnectDoes() {
        assertTrue(BinderLifecyclePolicy.canPublish(initial = true, isTrackedConnection = false))
        assertTrue(BinderLifecyclePolicy.canPublish(initial = false, isTrackedConnection = true))
        assertFalse(BinderLifecyclePolicy.canPublish(initial = false, isTrackedConnection = false))
    }
}

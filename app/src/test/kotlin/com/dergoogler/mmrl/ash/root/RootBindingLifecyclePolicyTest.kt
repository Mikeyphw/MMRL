package com.dergoogler.mmrl.ash.root

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootBindingLifecyclePolicyTest {
    @Test
    fun transientDisconnectClearsProxyButKeepsBindingForFrameworkReconnect() {
        val action = RootBindingLifecyclePolicy.action(RootBindingLifecyclePolicy.Event.DISCONNECTED)
        assertTrue(action.clearRemote)
        assertFalse(action.releaseBinding)
    }

    @Test
    fun bindingDeathReleasesConnectionSoNextCallCanBindFresh() {
        val action = RootBindingLifecyclePolicy.action(RootBindingLifecyclePolicy.Event.BINDING_DIED)
        assertTrue(action.clearRemote)
        assertTrue(action.releaseBinding)
    }

    @Test
    fun nullBindingIsReleasedRatherThanTrackedForever() {
        val action = RootBindingLifecyclePolicy.action(RootBindingLifecyclePolicy.Event.NULL_BINDING)
        assertTrue(action.clearRemote)
        assertTrue(action.releaseBinding)
    }
    @Test
    fun trackedDisconnectedBindingBlocksParallelBindUntilReconnectOrRelease() {
        assertTrue(
            RootBindingLifecyclePolicy.acquireAction(false, true) ==
                RootBindingLifecyclePolicy.AcquireAction.WAIT_FOR_TRACKED_RECONNECT,
        )
        assertTrue(
            RootBindingLifecyclePolicy.acquireAction(false, false) ==
                RootBindingLifecyclePolicy.AcquireAction.BIND_NEW,
        )
        assertTrue(
            RootBindingLifecyclePolicy.acquireAction(true, true) ==
                RootBindingLifecyclePolicy.AcquireAction.REUSE_LIVE,
        )
    }

    @Test
    fun initialPublicationDoesNotRequirePreexistingTrackedConnectionButReconnectDoes() {
        assertTrue(RootBindingLifecyclePolicy.canPublish(initial = true, isTrackedConnection = false))
        assertTrue(RootBindingLifecyclePolicy.canPublish(initial = false, isTrackedConnection = true))
        assertFalse(RootBindingLifecyclePolicy.canPublish(initial = false, isTrackedConnection = false))
    }
}

package com.dergoogler.mmrl.ui.activity.terminal

import com.dergoogler.mmrl.platform.model.ModId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PrivilegedLaunchSessionsTest {
    @Test
    fun actionLaunchCarriesOnlyOpaqueTokenIntoActivity() {
        val token = PrivilegedLaunchSessions.createAction(ModId("safe_module"))
        assertEquals("safe_module", PrivilegedLaunchSessions.getAction(token)?.moduleId?.id)
        assertNull(PrivilegedLaunchSessions.getAction("not-a-real-session"))
    }

    @Test
    fun emptyModuleCannotCreatePrivilegedActionSession() {
        assertThrows(IllegalArgumentException::class.java) {
            PrivilegedLaunchSessions.createAction(ModId.EMPTY)
        }
    }
}

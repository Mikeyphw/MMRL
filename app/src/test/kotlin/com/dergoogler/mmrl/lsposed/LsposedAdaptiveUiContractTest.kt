package com.dergoogler.mmrl.lsposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedAdaptiveUiContractTest {
    @Test
    fun `wide layout starts at contract breakpoint`() {
        assertFalse(LsposedUiContract.useListDetail(LsposedUiContract.listDetailBreakpointDp - 1))
        assertTrue(LsposedUiContract.useListDetail(LsposedUiContract.listDetailBreakpointDp))
    }

    @Test
    fun `description and safety limits preserve phone readability`() {
        assertEquals(3, LsposedUiContract.phoneDescriptionMaxLines)
        assertEquals(6, LsposedUiContract.detailRailDescriptionMaxLines)
        assertEquals(3, LsposedUiContract.visibleNoticeCount(8))
        assertEquals(2, LsposedUiContract.visibleNoticeCount(2))
    }

    @Test
    fun `detail rail stays usable on medium and expanded screens`() {
        assertTrue(LsposedUiContract.detailRailMinWidthDp >= 280)
        assertTrue(LsposedUiContract.listDetailBreakpointDp >= 720)
    }
}

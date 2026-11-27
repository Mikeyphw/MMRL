package com.dergoogler.mmrl.database.activity

import org.junit.Assert.*
import org.junit.Test

class ModuleMutationTicketTest {
    @Test fun reservationAndCallbackIdentityAreNotReused() {
        val tickets = ModuleMutationTickets<String>()
        val first = tickets.reserve("one")!!
        assertNull(tickets.reserve("one"))
        val unrelated = tickets.reserve("two")!!
        assertFalse(tickets.claim(first, "two"))
        assertTrue(tickets.claim(first, "one"))
        assertFalse(tickets.claim(first, "one"))
        assertNull(tickets.reserve("one"))
        assertTrue(tickets.release(first))
        val second = tickets.reserve("one")!!
        assertFalse(tickets.claim(first, "one"))
        assertFalse(tickets.release(first))
        assertTrue(tickets.claim(second, "one"))
        assertTrue(tickets.release(second))
        assertTrue(tickets.claim(unrelated, "two"))
        assertTrue(tickets.release(unrelated))
    }
}

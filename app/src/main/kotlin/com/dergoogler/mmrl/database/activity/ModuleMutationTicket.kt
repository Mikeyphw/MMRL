package com.dergoogler.mmrl.database.activity

/** One immutable request ticket, shared by all screens. Late callbacks cannot complete a successor. */
internal class ModuleMutationTickets<K> {
    internal class Ticket<K>(val key: K)
    private val active = mutableMapOf<K, Ticket<K>>()
    private val completing = mutableSetOf<Ticket<K>>()

    @Synchronized fun reserve(key: K): Ticket<K>? {
        if (active.containsKey(key)) return null
        return Ticket(key).also { active[key] = it }
    }

    @Synchronized fun claim(ticket: Ticket<K>, reportedKey: K): Boolean =
        ticket.key == reportedKey && active[ticket.key] === ticket && completing.add(ticket)

    @Synchronized fun release(ticket: Ticket<K>): Boolean {
        if (active[ticket.key] !== ticket) return false
        active.remove(ticket.key)
        completing.remove(ticket)
        return true
    }
}

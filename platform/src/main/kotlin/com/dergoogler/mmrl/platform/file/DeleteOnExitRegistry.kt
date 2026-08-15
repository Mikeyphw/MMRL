package com.dergoogler.mmrl.platform.file

/**
 * Process-lifetime delete-on-exit queue. Registration never performs deletion; shutdown drains
 * entries in reverse registration order so children registered after parents are removed first.
 */
internal class DeleteOnExitRegistry {
    data class Entry(val root: String, val relative: String)

    private val entries = LinkedHashSet<Entry>()

    @Synchronized
    fun register(entry: Entry): Boolean = entries.add(entry)

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    fun drain(delete: (Entry) -> Boolean): Boolean {
        val pending = synchronized(this) {
            val copy = entries.toList().asReversed()
            entries.clear()
            copy
        }
        return pending.fold(true) { ok, entry -> delete(entry) && ok }
    }
}

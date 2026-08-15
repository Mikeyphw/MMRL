package com.dergoogler.mmrl.platform.ksu

/** Preserves kernel GIDs the current UI enum does not know about; empty remains truly empty. */
object ProfileGroupPolicy {
    fun unknown(existing: List<Int>, known: Set<Int>): List<Int> = existing.filterNot(known::contains)

    fun merge(unknown: List<Int>, selectedKnown: List<Int>): List<Int> =
        (unknown + selectedKnown).distinct()
}

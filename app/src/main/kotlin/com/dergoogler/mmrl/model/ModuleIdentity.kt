package com.dergoogler.mmrl.model

import java.util.Locale

/** Stable module identity helpers for ordinary module-manager matching. */
object ModuleIdentity {
    fun normalize(id: String): String = id.trim().lowercase(Locale.ROOT)

    fun canonical(id: String): String = normalize(id)

    fun matches(left: String, right: String): Boolean = canonical(left) == canonical(right)

    fun aliasesFor(id: String): Set<String> = setOf(normalize(id))
}

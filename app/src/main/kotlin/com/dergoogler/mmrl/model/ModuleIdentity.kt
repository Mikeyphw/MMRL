package com.dergoogler.mmrl.model

import java.util.Locale

/**
 * Canonical identity used when correlating local modules with repository entries.
 * Repository author/name metadata can change and must never decide whether a module is installed.
 */
object ModuleIdentity {
    fun normalize(id: String): String = id.trim().lowercase(Locale.ROOT)

    fun matches(left: String, right: String): Boolean = normalize(left) == normalize(right)
}

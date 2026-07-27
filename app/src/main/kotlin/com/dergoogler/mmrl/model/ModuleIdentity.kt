package com.dergoogler.mmrl.model

import java.util.Locale

/**
 * Canonical identity used when correlating local modules with repository entries.
 * Repository author/name metadata can change and must never decide whether a module is installed.
 */
object ModuleIdentity {
    private val ashReXcueAliases = setOf(
        "ashlooper",
        "ashrexcue",
        "ashrexcuebootloopprotector",
    )

    fun normalize(id: String): String = id.trim().lowercase(Locale.ROOT)

    fun canonical(id: String): String {
        val normalized = normalize(id)
        return if (normalizedToken(normalized) in ashReXcueAliases) {
            ASH_REXCUE_CANONICAL_ID
        } else {
            normalized
        }
    }

    fun matches(left: String, right: String): Boolean = canonical(left) == canonical(right)

    fun isAshReXcue(id: String): Boolean = normalizedToken(id) in ashReXcueAliases

    fun aliasesFor(id: String): Set<String> =
        if (isAshReXcue(id)) {
            ashReXcueAliases + ASH_REXCUE_CANONICAL_ID
        } else {
            setOf(normalize(id))
        }

    private fun normalizedToken(id: String): String = normalize(id).filter(Char::isLetterOrDigit)

    private const val ASH_REXCUE_CANONICAL_ID = "ashrexcue"
}

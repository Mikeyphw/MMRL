package com.dergoogler.mmrl.platform.manager

/**
 * Turns filesystem marker operations into truthful, idempotent mutation results.
 * A pre-existing desired state succeeds without executing an unnecessary mutation.
 */
internal object ModuleMarkerMutation {
    fun ensureAbsent(exists: Boolean, delete: () -> Boolean): Boolean =
        !exists || delete()

    fun ensurePresent(exists: Boolean, create: () -> Boolean): Boolean =
        exists || create()
}

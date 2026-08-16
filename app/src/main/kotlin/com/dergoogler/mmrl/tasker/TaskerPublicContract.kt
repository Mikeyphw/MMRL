package com.dergoogler.mmrl.tasker

/** Stable Tasker output contract shared by all public Tasker responses. */
internal object TaskerPublicContract {
    const val VERSION = 2
    const val SCHEMA = "mmrl.tasker.output.v2"
    const val SOURCE = "MMRL"
    const val DELIVERY_INLINE = "INLINE"
    const val DELIVERY_URI_GRANT = "URI_GRANT"
}

internal enum class TaskerFreshness {
    FRESH,
    STALE,
    PARTIAL,
}

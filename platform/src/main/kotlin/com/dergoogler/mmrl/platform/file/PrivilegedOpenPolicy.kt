package com.dergoogler.mmrl.platform.file

import android.system.OsConstants

/** Pure planning for root-side open(2) calls so access classification and safety flags stay testable. */
internal object PrivilegedOpenPolicy {
    data class Plan(
        val access: PrivilegedPathPolicy.Access,
        val flags: Int,
        val mode: Int,
    )

    private val mutationFlags =
        OsConstants.O_WRONLY or
            OsConstants.O_RDWR or
            OsConstants.O_CREAT or
            OsConstants.O_TRUNC or
            OsConstants.O_APPEND

    fun plan(flags: Int, mode: Int): Plan {
        val access =
            if (flags and mutationFlags != 0) {
                PrivilegedPathPolicy.Access.MUTATE
            } else {
                PrivilegedPathPolicy.Access.READ
            }
        return Plan(
            access = access,
            flags = flags or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
            mode = mode,
        )
    }
}

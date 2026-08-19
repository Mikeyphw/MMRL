package com.dergoogler.mmrl.platform.file

/** Pure planning for root-side open(2) calls so access classification and safety flags stay testable. */
internal object PrivilegedOpenPolicy {
    data class Plan(
        val access: PrivilegedPathPolicy.Access,
        val flags: Int,
        val mode: Int,
    )

    // Linux open(2) flag values. Keep this policy independent of android.jar so
    // local JVM tests exercise the same classification logic used on-device.
    internal const val O_RDONLY = 0
    internal const val O_WRONLY = 0x0001
    internal const val O_RDWR = 0x0002
    internal const val O_CREAT = 0x0040
    internal const val O_TRUNC = 0x0200
    internal const val O_APPEND = 0x0400
    internal const val O_NOFOLLOW = 0x20000
    internal const val O_CLOEXEC = 0x80000

    private const val mutationFlags =
        O_WRONLY or O_RDWR or O_CREAT or O_TRUNC or O_APPEND

    fun plan(flags: Int, mode: Int): Plan {
        val access =
            if (flags and mutationFlags != 0) {
                PrivilegedPathPolicy.Access.MUTATE
            } else {
                PrivilegedPathPolicy.Access.READ
            }
        return Plan(
            access = access,
            flags = flags or O_CLOEXEC or O_NOFOLLOW,
            mode = mode,
        )
    }
}

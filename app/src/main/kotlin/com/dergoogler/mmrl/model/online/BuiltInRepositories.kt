package com.dergoogler.mmrl.model.online

object BuiltInRepositories {
    const val KERNELSU_NEXT_MODULES_URL =
        "https://raw.githubusercontent.com/KernelSU-Next/KernelSU-Next-Modules-Repo/refs/heads/main/modules.json"

    val all =
        listOf(
            ExploreRepository(
                name = "KernelSU Next Modules Repo",
                url = KERNELSU_NEXT_MODULES_URL,
                submission = "https://github.com/KernelSU-Next/KernelSU-Next-Modules-Repo",
                description =
                    "Community module catalog used by KernelSU Next. " +
                        "Entries are resolved from their latest GitHub release.",
            ),
        )
}

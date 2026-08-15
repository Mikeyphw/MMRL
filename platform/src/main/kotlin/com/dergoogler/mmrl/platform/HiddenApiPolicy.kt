package com.dergoogler.mmrl.platform

/** Narrow allowlist for the hidden framework surfaces MMRL actually proxies. */
internal object HiddenApiPolicy {
    val DEFAULT_PREFIXES = arrayOf(
        "Landroid/app/ActivityThread;",
        "Landroid/os/ServiceManager;",
        "Landroid/os/SystemProperties;",
        "Landroid/os/IUserManager;",
        "Landroid/content/pm/IPackageManager;",
        "Landroid/os/IPowerManager;",
    )

    fun areNarrow(prefixes: List<String>): Boolean {
        val approved = DEFAULT_PREFIXES.toSet()
        return prefixes.isNotEmpty() && prefixes.all(approved::contains)
    }
}

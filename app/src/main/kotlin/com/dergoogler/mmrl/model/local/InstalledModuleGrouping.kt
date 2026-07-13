package com.dergoogler.mmrl.model.local

import com.dergoogler.mmrl.platform.model.ModId

enum class InstalledModuleGroupKey {
    NEEDS_ATTENTION,
    UPDATES_AVAILABLE,
    ENABLED,
    DISABLED,
    PENDING_REMOVAL,
}

data class InstalledModuleGroup(
    val key: InstalledModuleGroupKey,
    val modules: List<LocalModule>,
)

/**
 * Places each installed module in exactly one visible group.
 *
 * Runtime states take priority over repository update metadata so a module pending removal or
 * already in the root manager's update state cannot also appear in the generic update group.
 */
fun groupInstalledModules(
    modules: List<LocalModule>,
    updateIds: Set<ModId>,
): List<InstalledModuleGroup> {
    val grouped =
        modules.groupBy { module ->
            when {
                module.state == State.REMOVE -> InstalledModuleGroupKey.PENDING_REMOVAL
                module.state == State.UPDATE -> InstalledModuleGroupKey.NEEDS_ATTENTION
                module.id in updateIds -> InstalledModuleGroupKey.UPDATES_AVAILABLE
                module.state == State.ENABLE -> InstalledModuleGroupKey.ENABLED
                else -> InstalledModuleGroupKey.DISABLED
            }
        }

    return InstalledModuleGroupKey.entries.mapNotNull { key ->
        grouped[key]
            ?.takeIf { it.isNotEmpty() }
            ?.let { InstalledModuleGroup(key = key, modules = it) }
    }
}

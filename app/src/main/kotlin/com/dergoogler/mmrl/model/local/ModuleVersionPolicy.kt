package com.dergoogler.mmrl.model.local

import com.dergoogler.mmrl.model.ModuleIdentity
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class ModuleVersionPolicyMode {
    FOLLOW_LATEST,
    IGNORE_UPDATES,
    PIN_CURRENT,
    MAX_VERSION_CODE,
}

@Serializable
data class ModuleVersionPolicy(
    val moduleId: String,
    val mode: ModuleVersionPolicyMode = ModuleVersionPolicyMode.FOLLOW_LATEST,
    val lockedVersion: String? = null,
    val lockedVersionCode: Int? = null,
    val maxVersionCode: Int? = null,
    val reason: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val normalizedModuleId: String
        get() = ModuleIdentity.normalize(moduleId)

    val isLocked: Boolean
        get() = mode != ModuleVersionPolicyMode.FOLLOW_LATEST

    fun blocks(candidateVersionCode: Int): Boolean = when (mode) {
        ModuleVersionPolicyMode.FOLLOW_LATEST -> false
        ModuleVersionPolicyMode.IGNORE_UPDATES -> true
        ModuleVersionPolicyMode.PIN_CURRENT -> lockedVersionCode?.let { candidateVersionCode > it } ?: true
        ModuleVersionPolicyMode.MAX_VERSION_CODE -> maxVersionCode?.let { candidateVersionCode > it } ?: false
    }

    fun statusLabel(candidateVersion: String? = null): String = when (mode) {
        ModuleVersionPolicyMode.FOLLOW_LATEST -> "Following latest"
        ModuleVersionPolicyMode.IGNORE_UPDATES -> "Updates ignored"
        ModuleVersionPolicyMode.PIN_CURRENT -> buildString {
            append("Locked")
            lockedVersion?.takeIf(String::isNotBlank)?.let { append(" at ").append(it) }
            candidateVersion?.takeIf(String::isNotBlank)?.let { append(" · newer ").append(it) }
        }
        ModuleVersionPolicyMode.MAX_VERSION_CODE -> buildString {
            append("Max")
            maxVersionCode?.let { append(" ").append(it) }
            candidateVersion?.takeIf(String::isNotBlank)?.let { append(" · newer ").append(it) }
        }
    }

    companion object {
        fun follow(moduleId: String) = ModuleVersionPolicy(
            moduleId = ModuleIdentity.normalize(moduleId),
            mode = ModuleVersionPolicyMode.FOLLOW_LATEST,
        )

        fun ignore(moduleId: String) = ModuleVersionPolicy(
            moduleId = ModuleIdentity.normalize(moduleId),
            mode = ModuleVersionPolicyMode.IGNORE_UPDATES,
            reason = "Ignored from Installed modules",
        )

        fun pinCurrent(
            moduleId: String,
            version: String,
            versionCode: Int,
        ) = ModuleVersionPolicy(
            moduleId = ModuleIdentity.normalize(moduleId),
            mode = ModuleVersionPolicyMode.PIN_CURRENT,
            lockedVersion = version,
            lockedVersionCode = versionCode,
            maxVersionCode = versionCode,
            reason = "Pinned current stable version",
        )

        fun maxCurrent(
            moduleId: String,
            version: String,
            versionCode: Int,
        ) = ModuleVersionPolicy(
            moduleId = ModuleIdentity.normalize(moduleId),
            mode = ModuleVersionPolicyMode.MAX_VERSION_CODE,
            lockedVersion = version,
            lockedVersionCode = versionCode,
            maxVersionCode = versionCode,
            reason = "Allowed only up to this version",
        )
    }
}

@Serializable
data class ModuleSnapshot(
    val id: String,
    val label: String,
    val createdAt: Long,
    val manager: String,
    val androidSdk: Int,
    val metadataOnly: Boolean = true,
    val cachedZipCount: Int = 0,
    val modules: List<ModuleSnapshotItem>,
) {
    val enabledCount: Int
        get() = modules.count { it.enabled }
}

@Serializable
data class ModuleSnapshotItem(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Int,
    val author: String,
    val description: String,
    val enabled: Boolean,
    val state: String,
    val size: Long,
    val lastUpdated: Long,
    val policy: ModuleVersionPolicy? = null,
    val ashTrustState: String? = null,
)

enum class ModuleSnapshotPlanStatus {
    CURRENT,
    VERSION_CHANGED,
    STATE_CHANGED,
    MISSING,
    EXTRA,
    REVIEW,
}

data class ModuleSnapshotPlanItem(
    val moduleId: String,
    val title: String,
    val status: ModuleSnapshotPlanStatus,
    val summary: String,
    val destructive: Boolean = false,
)

object ModuleSnapshotPlanner {
    fun compare(
        snapshot: ModuleSnapshot,
        current: List<ModuleSnapshotItem>,
    ): List<ModuleSnapshotPlanItem> {
        val currentById = current.associateBy { ModuleIdentity.normalize(it.id) }
        val snapshotById = snapshot.modules.associateBy { ModuleIdentity.normalize(it.id) }
        val planned = buildList {
            snapshot.modules.forEach { saved ->
                val currentItem = currentById[ModuleIdentity.normalize(saved.id)]
                when {
                    currentItem == null -> add(
                        ModuleSnapshotPlanItem(
                            moduleId = saved.id,
                            title = saved.name,
                            status = ModuleSnapshotPlanStatus.MISSING,
                            summary = "Not installed now; restore plan would reinstall ${saved.version} after review.",
                        ),
                    )
                    currentItem.versionCode != saved.versionCode || currentItem.version != saved.version -> add(
                        ModuleSnapshotPlanItem(
                            moduleId = saved.id,
                            title = saved.name,
                            status = ModuleSnapshotPlanStatus.VERSION_CHANGED,
                            summary = "Snapshot has ${saved.version}; device has ${currentItem.version}. Review before downgrade or reinstall.",
                        ),
                    )
                    currentItem.enabled != saved.enabled -> add(
                        ModuleSnapshotPlanItem(
                            moduleId = saved.id,
                            title = saved.name,
                            status = ModuleSnapshotPlanStatus.STATE_CHANGED,
                            summary = if (saved.enabled) "Snapshot expected enabled; device is disabled." else "Snapshot expected disabled; device is enabled.",
                        ),
                    )
                    else -> add(
                        ModuleSnapshotPlanItem(
                            moduleId = saved.id,
                            title = saved.name,
                            status = ModuleSnapshotPlanStatus.CURRENT,
                            summary = "Matches the saved snapshot.",
                        ),
                    )
                }
            }
            current.filter { currentItem -> snapshotById[ModuleIdentity.normalize(currentItem.id)] == null }
                .forEach { extra ->
                    add(
                        ModuleSnapshotPlanItem(
                            moduleId = extra.id,
                            title = extra.name,
                            status = ModuleSnapshotPlanStatus.EXTRA,
                            summary = "Installed after the snapshot; review before removing.",
                            destructive = true,
                        ),
                    )
                }
        }

        return planned.sortedWith(
            compareBy<ModuleSnapshotPlanItem> { it.status == ModuleSnapshotPlanStatus.CURRENT }
                .thenByDescending { it.destructive }
                .thenBy { it.title.lowercase(Locale.ROOT) },
        )
    }
}

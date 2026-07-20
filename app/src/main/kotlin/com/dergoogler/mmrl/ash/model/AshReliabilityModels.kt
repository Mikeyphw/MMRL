package com.dergoogler.mmrl.ash.model

import java.security.MessageDigest

enum class AshStateHealthLevel {
    Healthy,
    Degraded,
    RepairRequired,
}

data class AshModuleHealth(
    val schemaVersion: Int = 0,
    val status: String = "unknown",
    val issueCount: Int = 0,
    val repairCount: Int = 0,
    val lastRepairAt: Long = 0,
    val summary: String = "",
)

data class AshStateIssue(
    val code: String,
    val title: String,
    val detail: String,
    val repairable: Boolean,
)

data class AshStateHealth(
    val level: AshStateHealthLevel = AshStateHealthLevel.Healthy,
    val issues: List<AshStateIssue> = emptyList(),
    val module: AshModuleHealth = AshModuleHealth(),
    val cacheEvents: List<String> = emptyList(),
) {
    val repairRecommended: Boolean
        get() = level == AshStateHealthLevel.RepairRequired || issues.any(AshStateIssue::repairable)

    val summary: String
        get() = when (level) {
            AshStateHealthLevel.Healthy -> "Recovery state is healthy"
            AshStateHealthLevel.Degraded -> "Recovery state needs review"
            AshStateHealthLevel.RepairRequired -> "Recovery state repair is recommended"
        }
}


object AshSnapshotIntegrity {
    const val ALGORITHM = "SHA-256"

    fun checksum(savedAt: Long, moduleStateRaw: String, snapshotRaw: String): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        digest.update(savedAt.toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(moduleStateRaw.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(snapshotRaw.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

object AshStateHealthEngine {
    private const val SUPPORTED_SNAPSHOT_SCHEMA_MIN = 1
    private const val SUPPORTED_SNAPSHOT_SCHEMA_MAX = 2
    private const val CACHE_STALE_SECONDS = 24L * 60L * 60L
    private const val FUTURE_TOLERANCE_SECONDS = 5L * 60L

    fun assess(
        snapshot: AshSnapshot?,
        source: AshSnapshotSource,
        cacheEvents: List<String> = emptyList(),
        nowSeconds: Long = System.currentTimeMillis() / 1_000L,
    ): AshStateHealth {
        if (snapshot == null) {
            return AshStateHealth(
                level = AshStateHealthLevel.Degraded,
                issues = listOf(
                    AshStateIssue(
                        code = "snapshot-missing",
                        title = "No recovery snapshot",
                        detail = "A live or cached AshReXcue snapshot is not available.",
                        repairable = false,
                    ),
                ),
                cacheEvents = cacheEvents.distinct(),
            )
        }

        val issues = buildList {
            if (snapshot.schemaVersion !in SUPPORTED_SNAPSHOT_SCHEMA_MIN..SUPPORTED_SNAPSHOT_SCHEMA_MAX) {
                add(
                    AshStateIssue(
                        code = "snapshot-schema",
                        title = "Unsupported state schema",
                        detail = "Snapshot schema ${snapshot.schemaVersion} is outside the supported range.",
                        repairable = false,
                    ),
                )
            }
            if (snapshot.recoveryRevision.isBlank()) {
                add(
                    AshStateIssue(
                        code = "revision-missing",
                        title = "Recovery revision missing",
                        detail = "Guarded recovery plans cannot safely bind to this snapshot.",
                        repairable = true,
                    ),
                )
            }
            if (snapshot.generatedAt > nowSeconds + FUTURE_TOLERANCE_SECONDS) {
                add(
                    AshStateIssue(
                        code = "clock-future",
                        title = "Snapshot time is in the future",
                        detail = "Device clock drift can make recovery evidence unreliable.",
                        repairable = false,
                    ),
                )
            }
            if (source == AshSnapshotSource.Cache && snapshot.generatedAt > 0 && nowSeconds - snapshot.generatedAt > CACHE_STALE_SECONDS) {
                add(
                    AshStateIssue(
                        code = "cache-stale",
                        title = "Cached evidence is stale",
                        detail = "The last successful snapshot is older than 24 hours.",
                        repairable = false,
                    ),
                )
            }

            val folders = HashSet<String>()
            val ids = HashSet<String>()
            snapshot.modules.forEach { module ->
                if (!folders.add(module.folder)) {
                    add(
                        AshStateIssue(
                            code = "duplicate-folder:${module.folder}",
                            title = "Duplicate module folder",
                            detail = "${module.folder} appears more than once in the module inventory.",
                            repairable = true,
                        ),
                    )
                }
                if (module.id.isNotBlank() && !ids.add(module.id)) {
                    add(
                        AshStateIssue(
                            code = "duplicate-id:${module.id}",
                            title = "Duplicate module identity",
                            detail = "${module.id} is reported by multiple module folders.",
                            repairable = false,
                        ),
                    )
                }
            }

            val modulesByFolder = snapshot.modules.associateBy(ModuleItem::folder)
            val quarantineFolders = HashSet<String>()
            snapshot.quarantine.forEach { item ->
                if (!quarantineFolders.add(item.folder)) {
                    add(
                        AshStateIssue(
                            code = "duplicate-quarantine:${item.folder}",
                            title = "Duplicate quarantine marker",
                            detail = "${item.folder} has multiple quarantine records.",
                            repairable = true,
                        ),
                    )
                }
                val module = modulesByFolder[item.folder]
                when {
                    module == null || !item.exists -> add(
                        AshStateIssue(
                            code = "orphan-quarantine:${item.folder}",
                            title = "Orphan quarantine marker",
                            detail = "${item.folder} is quarantined but the module folder is missing.",
                            repairable = true,
                        ),
                    )
                    !item.disablePresent || module.enabled -> add(
                        AshStateIssue(
                            code = "quarantine-enabled:${item.folder}",
                            title = "Quarantine state is inconsistent",
                            detail = "${item.folder} is marked quarantined but is not disabled.",
                            repairable = true,
                        ),
                    )
                }
            }

            if (snapshot.dashboard.restoreState == "testing" && snapshot.dashboard.restoreCount <= 0) {
                add(
                    AshStateIssue(
                        code = "trial-empty",
                        title = "Restoration trial has no modules",
                        detail = "The active trial record is incomplete and should be repaired.",
                        repairable = true,
                    ),
                )
            }
            if (snapshot.health.status.equals("repair-required", ignoreCase = true) || snapshot.health.issueCount > 0) {
                add(
                    AshStateIssue(
                        code = "module-health",
                        title = "AshReXcue reported state issues",
                        detail = snapshot.health.summary.ifBlank {
                            "The embedded module reported ${snapshot.health.issueCount} state issue(s)."
                        },
                        repairable = true,
                    ),
                )
            }
            cacheEvents.distinct().forEach { event ->
                when (event) {
                    "cache-recovered" -> add(
                        AshStateIssue(
                            code = event,
                            title = "Snapshot cache recovered",
                            detail = "The primary cache was damaged and restored from the last-good copy.",
                            repairable = false,
                        ),
                    )
                    "cache-migrated" -> add(
                        AshStateIssue(
                            code = event,
                            title = "Snapshot cache migrated",
                            detail = "A legacy cache envelope was upgraded to the checksummed format.",
                            repairable = false,
                        ),
                    )
                    "cache-corrupt" -> add(
                        AshStateIssue(
                            code = event,
                            title = "Snapshot cache corruption detected",
                            detail = "A damaged cache file was quarantined for diagnostics.",
                            repairable = false,
                        ),
                    )
                }
            }
        }.distinctBy(AshStateIssue::code)

        val level = when {
            issues.any { it.repairable && it.code !in setOf("cache-migrated") } -> AshStateHealthLevel.RepairRequired
            issues.isNotEmpty() -> AshStateHealthLevel.Degraded
            else -> AshStateHealthLevel.Healthy
        }
        return AshStateHealth(
            level = level,
            issues = issues,
            module = snapshot.health,
            cacheEvents = cacheEvents.distinct(),
        )
    }
}

package com.dergoogler.mmrl.ash.model

enum class AshReleaseGateStatus {
    Ready,
    ReadyWithWarnings,
    Blocked,
}

enum class AshReleaseCheckState {
    Pass,
    Warning,
    Blocker,
}

data class AshReleaseCheck(
    val id: String,
    val title: String,
    val state: AshReleaseCheckState,
    val detail: String,
)

data class AshModuleReleaseGate(
    val protocolVersion: String = "",
    val generatedAt: Long = 0,
    val moduleVersion: String = "",
    val moduleVersionCode: Int = 0,
    val status: AshReleaseGateStatus = AshReleaseGateStatus.Blocked,
    val checks: List<AshReleaseCheck> = emptyList(),
)

data class AshReleaseGateReport(
    val protocolVersion: String = PROTOCOL_VERSION,
    val generatedAt: Long = 0,
    val status: AshReleaseGateStatus = AshReleaseGateStatus.Blocked,
    val checks: List<AshReleaseCheck> = emptyList(),
    val module: AshModuleReleaseGate? = null,
) {
    val blockerCount: Int
        get() = checks.count { it.state == AshReleaseCheckState.Blocker }

    val warningCount: Int
        get() = checks.count { it.state == AshReleaseCheckState.Warning }

    val passedCount: Int
        get() = checks.count { it.state == AshReleaseCheckState.Pass }

    val summary: String
        get() = when (status) {
            AshReleaseGateStatus.Ready -> "AshReXcue is ready for release"
            AshReleaseGateStatus.ReadyWithWarnings ->
                "AshReXcue is release-ready with $warningCount warning${if (warningCount == 1) "" else "s"}"
            AshReleaseGateStatus.Blocked ->
                "Release is blocked by $blockerCount check${if (blockerCount == 1) "" else "s"}"
        }

    companion object {
        const val PROTOCOL_VERSION = "mmrl.ash.release.v1"
    }
}

object AshReleaseGateEngine {
    const val MODULE_PROTOCOL_VERSION = "ashrexcue.release.v1"
    const val REQUIRED_SNAPSHOT_SCHEMA = 2
    const val MAX_MODULES_WITHOUT_WARNING = 512
    const val MAX_ACTIVITY_ITEMS_WITHOUT_WARNING = 200

    val REQUIRED_FEATURES: Set<String> = setOf(
        "snapshot",
        "capabilities",
        "trust-management",
        "restore-trials",
        "guided-recovery",
        "recovery-plans",
        "diagnostic-export",
        "external-control-v1",
        "automation-receipts",
        "one-shot-tokens",
        "idempotency",
        "state-health",
        "state-repair",
        "snapshot-schema-2",
        "release-gate-v1",
    )

    fun assess(
        rootAvailable: Boolean,
        lifecycle: AshModuleLifecycle,
        snapshot: AshSnapshot?,
        source: AshSnapshotSource,
        health: AshStateHealth,
        moduleGate: AshModuleReleaseGate?,
        nowSeconds: Long = System.currentTimeMillis() / 1_000L,
    ): AshReleaseGateReport {
        val checks = buildList {
            add(
                check(
                    id = "root-access",
                    title = "Root access",
                    passed = rootAvailable,
                    failureState = AshReleaseCheckState.Blocker,
                    passDetail = "Root access is available to the typed AshReXcue service.",
                    failDetail = "Root access is required for live release verification.",
                ),
            )
            add(
                check(
                    id = "live-snapshot",
                    title = "Live recovery snapshot",
                    passed = source == AshSnapshotSource.Live && snapshot != null,
                    failureState = AshReleaseCheckState.Blocker,
                    passDetail = "The release gate is using live recovery state.",
                    failDetail = "Cached or missing recovery state cannot pass the final release gate.",
                ),
            )

            val lifecycleState = when {
                !lifecycle.compatible -> AshReleaseCheckState.Blocker
                lifecycle.rebootRequired -> AshReleaseCheckState.Blocker
                lifecycle.updateAvailable -> AshReleaseCheckState.Blocker
                lifecycle.state == AshModuleLifecycleState.Newer -> AshReleaseCheckState.Warning
                lifecycle.state == AshModuleLifecycleState.Current -> AshReleaseCheckState.Pass
                else -> AshReleaseCheckState.Warning
            }
            add(
                AshReleaseCheck(
                    id = "module-lifecycle",
                    title = "Bundled module parity",
                    state = lifecycleState,
                    detail = when (lifecycleState) {
                        AshReleaseCheckState.Pass -> "Installed and bundled AshReXcue versions match."
                        AshReleaseCheckState.Warning -> lifecycle.compatibilityMessage.ifBlank {
                            "The installed module is compatible but not an exact bundled-version match."
                        }
                        AshReleaseCheckState.Blocker -> lifecycle.compatibilityMessage.ifBlank {
                            "Install the bundled AshReXcue module and reboot before release verification."
                        }
                    },
                ),
            )

            if (snapshot == null) {
                add(blocker("api-contract", "Typed API contract", "No live capability report is available."))
                add(blocker("snapshot-schema", "Snapshot schema", "No snapshot schema is available."))
                add(blocker("feature-parity", "Feature parity", "No module feature report is available."))
                add(blocker("recovery-revision", "Recovery revision", "No recovery revision is available."))
                add(blocker("restoration-idle", "Restoration state", "Restoration state is unavailable."))
                add(blocker("quarantine-integrity", "Quarantine integrity", "Quarantine state is unavailable."))
                add(blocker("performance-budget", "Snapshot performance budget", "Snapshot size cannot be assessed."))
            } else {
                val apiCompatible = snapshot.capabilities.apiVersion in
                    AshModuleLifecycleResolver.SUPPORTED_API_MIN..AshModuleLifecycleResolver.SUPPORTED_API_MAX &&
                    snapshot.capabilities.minimumClientApi <= AshModuleLifecycleResolver.SUPPORTED_API_MAX
                add(
                    check(
                        id = "api-contract",
                        title = "Typed API contract",
                        passed = apiCompatible,
                        failureState = AshReleaseCheckState.Blocker,
                        passDetail = "Module API ${snapshot.capabilities.apiVersion} matches the MMRL client contract.",
                        failDetail = "Module API ${snapshot.capabilities.apiVersion} is incompatible with this MMRL build.",
                    ),
                )
                add(
                    check(
                        id = "snapshot-schema",
                        title = "Snapshot schema",
                        passed = snapshot.schemaVersion == REQUIRED_SNAPSHOT_SCHEMA,
                        failureState = AshReleaseCheckState.Blocker,
                        passDetail = "Snapshot schema ${snapshot.schemaVersion} is the release schema.",
                        failDetail = "Snapshot schema ${snapshot.schemaVersion} does not match release schema $REQUIRED_SNAPSHOT_SCHEMA.",
                    ),
                )
                val missingFeatures = REQUIRED_FEATURES - snapshot.capabilities.features
                add(
                    check(
                        id = "feature-parity",
                        title = "Feature parity",
                        passed = missingFeatures.isEmpty(),
                        failureState = AshReleaseCheckState.Blocker,
                        passDetail = "All ${REQUIRED_FEATURES.size} required recovery capabilities are present.",
                        failDetail = "Missing required capabilities: ${missingFeatures.sorted().joinToString()}.",
                    ),
                )
                add(
                    check(
                        id = "recovery-revision",
                        title = "Recovery revision",
                        passed = snapshot.recoveryRevision.isNotBlank(),
                        failureState = AshReleaseCheckState.Blocker,
                        passDetail = "Guarded recovery operations can bind to revision ${snapshot.recoveryRevision}.",
                        failDetail = "A stable recovery revision is required for guarded mutations.",
                    ),
                )
                add(
                    check(
                        id = "restoration-idle",
                        title = "Restoration state",
                        passed = snapshot.dashboard.restoreState != "testing",
                        failureState = AshReleaseCheckState.Blocker,
                        passDetail = "No restoration trial is active.",
                        failDetail = "Complete or roll back the active restoration trial before release.",
                    ),
                )
                val staleQuarantine = snapshot.quarantine.filter { !it.exists || !it.disablePresent }
                add(
                    check(
                        id = "quarantine-integrity",
                        title = "Quarantine integrity",
                        passed = staleQuarantine.isEmpty(),
                        failureState = AshReleaseCheckState.Blocker,
                        passDetail = "All quarantine records match existing disabled modules.",
                        failDetail = "Stale quarantine records: ${staleQuarantine.joinToString { it.folder }}.",
                    ),
                )
                val performanceWarning = snapshot.modules.size > MAX_MODULES_WITHOUT_WARNING ||
                    snapshot.activity.size > MAX_ACTIVITY_ITEMS_WITHOUT_WARNING
                add(
                    AshReleaseCheck(
                        id = "performance-budget",
                        title = "Snapshot performance budget",
                        state = if (performanceWarning) AshReleaseCheckState.Warning else AshReleaseCheckState.Pass,
                        detail = if (performanceWarning) {
                            "Large recovery state detected: ${snapshot.modules.size} modules and ${snapshot.activity.size} activity items."
                        } else {
                            "Snapshot remains within the ${MAX_MODULES_WITHOUT_WARNING}-module and ${MAX_ACTIVITY_ITEMS_WITHOUT_WARNING}-activity budgets."
                        },
                    ),
                )
                add(
                    AshReleaseCheck(
                        id = "pending-settings",
                        title = "Queued settings",
                        state = if (snapshot.pendingSettings.isEmpty()) AshReleaseCheckState.Pass else AshReleaseCheckState.Warning,
                        detail = if (snapshot.pendingSettings.isEmpty()) {
                            "No boot-protection settings are waiting for reboot."
                        } else {
                            "${snapshot.pendingSettings.size} setting change${if (snapshot.pendingSettings.size == 1) " is" else "s are"} waiting for reboot."
                        },
                    ),
                )
                val ageSeconds = if (snapshot.generatedAt <= 0) Long.MAX_VALUE else nowSeconds - snapshot.generatedAt
                add(
                    AshReleaseCheck(
                        id = "snapshot-freshness",
                        title = "Snapshot freshness",
                        state = if (ageSeconds in 0..300) AshReleaseCheckState.Pass else AshReleaseCheckState.Warning,
                        detail = if (ageSeconds in 0..300) {
                            "The release snapshot is fresh."
                        } else {
                            "Refresh AshReXcue immediately before publishing the release."
                        },
                    ),
                )
            }

            add(
                when (health.level) {
                    AshStateHealthLevel.Healthy -> AshReleaseCheck(
                        id = "state-health",
                        title = "Durable state health",
                        state = AshReleaseCheckState.Pass,
                        detail = "Durable recovery state is healthy.",
                    )
                    AshStateHealthLevel.Degraded -> AshReleaseCheck(
                        id = "state-health",
                        title = "Durable state health",
                        state = AshReleaseCheckState.Warning,
                        detail = health.summary,
                    )
                    AshStateHealthLevel.RepairRequired -> AshReleaseCheck(
                        id = "state-health",
                        title = "Durable state health",
                        state = AshReleaseCheckState.Blocker,
                        detail = health.summary,
                    )
                },
            )

            if (moduleGate == null) {
                add(blocker("module-self-test", "Embedded module self-test", "The module release-gate report is unavailable."))
            } else {
                val protocolMatches = moduleGate.protocolVersion == MODULE_PROTOCOL_VERSION
                add(
                    check(
                        id = "module-release-protocol",
                        title = "Module release protocol",
                        passed = protocolMatches,
                        failureState = AshReleaseCheckState.Blocker,
                        passDetail = "Module release protocol ${moduleGate.protocolVersion} is supported.",
                        failDetail = "Unsupported module release protocol ${moduleGate.protocolVersion.ifBlank { "missing" }}.",
                    ),
                )
                add(
                    AshReleaseCheck(
                        id = "module-self-test",
                        title = "Embedded module self-test",
                        state = when (moduleGate.status) {
                            AshReleaseGateStatus.Ready -> AshReleaseCheckState.Pass
                            AshReleaseGateStatus.ReadyWithWarnings -> AshReleaseCheckState.Warning
                            AshReleaseGateStatus.Blocked -> AshReleaseCheckState.Blocker
                        },
                        detail = when (moduleGate.status) {
                            AshReleaseGateStatus.Ready -> "All ${moduleGate.checks.size} embedded module checks passed."
                            AshReleaseGateStatus.ReadyWithWarnings -> "The embedded module reported release warnings."
                            AshReleaseGateStatus.Blocked -> "The embedded module reported a blocking release failure."
                        },
                    ),
                )
                moduleGate.checks.forEach { moduleCheck ->
                    add(
                        moduleCheck.copy(
                            id = "module:${moduleCheck.id}",
                            title = "Module · ${moduleCheck.title}",
                        ),
                    )
                }
            }
        }.distinctBy(AshReleaseCheck::id)

        val status = when {
            checks.any { it.state == AshReleaseCheckState.Blocker } -> AshReleaseGateStatus.Blocked
            checks.any { it.state == AshReleaseCheckState.Warning } -> AshReleaseGateStatus.ReadyWithWarnings
            else -> AshReleaseGateStatus.Ready
        }
        return AshReleaseGateReport(
            generatedAt = nowSeconds,
            status = status,
            checks = checks,
            module = moduleGate,
        )
    }

    private fun check(
        id: String,
        title: String,
        passed: Boolean,
        failureState: AshReleaseCheckState,
        passDetail: String,
        failDetail: String,
    ) = AshReleaseCheck(
        id = id,
        title = title,
        state = if (passed) AshReleaseCheckState.Pass else failureState,
        detail = if (passed) passDetail else failDetail,
    )

    private fun blocker(id: String, title: String, detail: String) = AshReleaseCheck(
        id = id,
        title = title,
        state = AshReleaseCheckState.Blocker,
        detail = detail,
    )
}

package com.dergoogler.mmrl.ash

import com.dergoogler.mmrl.ash.data.AshBundledModuleProvider
import com.dergoogler.mmrl.ash.data.AshModuleInstaller
import com.dergoogler.mmrl.ash.data.AshRepository
import com.dergoogler.mmrl.ash.data.AshSnapshotStore
import com.dergoogler.mmrl.ash.model.AshGuidanceOutcome
import com.dergoogler.mmrl.ash.model.AshInstallMode
import com.dergoogler.mmrl.ash.model.AshManagerState
import com.dergoogler.mmrl.ash.model.AshModuleInstallation
import com.dergoogler.mmrl.ash.model.AshModuleLifecycleResolver
import com.dergoogler.mmrl.ash.model.AshRecoveryGuardSeverity
import com.dergoogler.mmrl.ash.model.AshRecoveryPlan
import com.dergoogler.mmrl.ash.model.AshRecoveryPlanEngine
import com.dergoogler.mmrl.ash.model.AshRecoveryPlanPreset
import com.dergoogler.mmrl.ash.model.AshReleaseGateEngine
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
import com.dergoogler.mmrl.ash.model.AshStateHealthEngine
import com.dergoogler.mmrl.ash.model.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
// Central Phase A lifecycle and snapshot coordinator.
class AshReXcueManager @Inject constructor(
    private val repository: AshRepository,
    private val snapshotStore: AshSnapshotStore,
    private val bundledModuleProvider: AshBundledModuleProvider,
    private val moduleInstaller: AshModuleInstaller,
) {
    private val refreshMutex = Mutex()
    private var lastRefreshCompletedAt = 0L
    private var lastRefreshFailedAt = 0L
    private val _state = MutableStateFlow(AshManagerState())
    val state: StateFlow<AshManagerState> = _state.asStateFlow()

    suspend fun refresh(): AshManagerState = refreshInternal(force = true)

    suspend fun refreshIfStale(maxAgeMillis: Long = 30_000L): AshManagerState =
        refreshInternal(force = false, maxAgeMillis = maxAgeMillis)

    private suspend fun refreshInternal(
        force: Boolean,
        maxAgeMillis: Long = 0L,
    ): AshManagerState = refreshMutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && _state.value.snapshot != null && now - lastRefreshCompletedAt <= maxAgeMillis) {
            return@withLock _state.value
        }
        if (!force && lastRefreshFailedAt > 0L && now - lastRefreshFailedAt < FAILURE_BACKOFF_MILLIS) {
            return@withLock _state.value
        }
        val bundled = runCatching { bundledModuleProvider.metadata() }
            .getOrElse { error ->
                return@withLock publish(
                    AshManagerState(liveError = error.message ?: "Unable to inspect bundled AshReXcue module"),
                )
            }
        val rootAvailable = repository.rootAvailable()
        var currentInstallation: AshModuleInstallation? = null
        var currentModuleStateRaw: String? = null
        var liveError: String? = if (rootAvailable) null else "Root access is unavailable"

        if (rootAvailable) {
            runCatching { repository.moduleStateRaw() }
                .onSuccess { raw ->
                    currentModuleStateRaw = raw
                    currentInstallation = repository.parseModuleInstallation(raw)
                }
                .onFailure { error ->
                    liveError = error.message ?: "Unable to inspect the AshReXcue module"
                }

            val installation = currentInstallation
            if (installation?.controlAvailable == true) {
                val live = runCatching {
                    val snapshotRaw = repository.snapshotRaw()
                    val snapshot = repository.parseSnapshot(snapshotRaw)
                    val lifecycle = AshModuleLifecycleResolver.resolve(
                        installation = installation,
                        bundled = bundled,
                        capabilities = snapshot.capabilities,
                    )
                    val moduleGate = if (snapshot.capabilities.supports("release-gate-v1")) {
                        runCatching {
                            repository.parseReleaseGate(repository.releaseGateRaw())
                        }.getOrNull()
                    } else {
                        null
                    }
                    LiveRefresh(snapshotRaw, snapshot, lifecycle, moduleGate)
                }
                live.onSuccess { result ->
                    val snapshotRaw = result.snapshotRaw
                    val snapshot = result.snapshot
                    val lifecycle = result.lifecycle
                    val health = AshStateHealthEngine.assess(snapshot, AshSnapshotSource.Live)
                    if (lifecycle.compatible) {
                        snapshotStore.write(
                            moduleStateRaw = requireNotNull(currentModuleStateRaw),
                            snapshotRaw = snapshotRaw,
                        )
                    }
                    return@withLock publish(
                        AshManagerState(
                            rootAvailable = true,
                            lifecycle = lifecycle,
                            snapshot = snapshot,
                            source = AshSnapshotSource.Live,
                            readOnly = !lifecycle.compatible,
                            lastSuccessfulAt = snapshot.generatedAt,
                            liveError = if (lifecycle.compatible) null else lifecycle.compatibilityMessage,
                            health = health,
                            releaseGate = AshReleaseGateEngine.assess(
                                rootAvailable = true,
                                lifecycle = lifecycle,
                                snapshot = snapshot,
                                source = AshSnapshotSource.Live,
                                health = health,
                                moduleGate = result.moduleGate,
                            ),
                        ),
                    )
                }.onFailure { error ->
                    liveError = error.message ?: "Unable to read the AshReXcue snapshot"
                }
            }
        }

        val cachedResult = snapshotStore.read()
        val cached = cachedResult.entry
        if (cached != null) {
            val cachedState = runCatching {
                val cachedInstallation = repository.parseModuleInstallation(cached.moduleStateRaw)
                val snapshot = repository.parseSnapshot(cached.snapshotRaw)
                val installation = currentInstallation ?: cachedInstallation
                val capabilities = snapshot.capabilities.takeIf { capabilities ->
                    currentInstallation == null ||
                        capabilities.moduleVersionCode == 0 ||
                        capabilities.moduleVersionCode == installation.versionCode
                }
                val lifecycle = AshModuleLifecycleResolver.resolve(
                    installation = installation,
                    bundled = bundled,
                    capabilities = capabilities,
                    liveError = liveError,
                )
                val health = AshStateHealthEngine.assess(
                    snapshot = snapshot,
                    source = AshSnapshotSource.Cache,
                    cacheEvents = cachedResult.events,
                )
                AshManagerState(
                    rootAvailable = rootAvailable,
                    lifecycle = lifecycle,
                    snapshot = snapshot,
                    source = AshSnapshotSource.Cache,
                    readOnly = true,
                    lastSuccessfulAt = cached.savedAt,
                    liveError = liveError ?: "Live AshReXcue status is unavailable",
                    health = health,
                    releaseGate = AshReleaseGateEngine.assess(
                        rootAvailable = rootAvailable,
                        lifecycle = lifecycle,
                        snapshot = snapshot,
                        source = AshSnapshotSource.Cache,
                        health = health,
                        moduleGate = null,
                    ),
                )
            }.getOrNull()
            if (cachedState != null) {
                if (liveError != null) lastRefreshFailedAt = System.currentTimeMillis()
                return@withLock publish(cachedState)
            }
        }

        val installation = currentInstallation ?: AshModuleInstallation()
        val lifecycle = AshModuleLifecycleResolver.resolve(
            installation = installation,
            bundled = bundled,
            capabilities = null,
            liveError = liveError,
        )
        val health = AshStateHealthEngine.assess(null, AshSnapshotSource.None, cachedResult.events)
        publish(
            AshManagerState(
                rootAvailable = rootAvailable,
                lifecycle = lifecycle,
                source = AshSnapshotSource.None,
                readOnly = true,
                liveError = liveError,
                health = health,
                releaseGate = AshReleaseGateEngine.assess(
                    rootAvailable = rootAvailable,
                    lifecycle = lifecycle,
                    snapshot = null,
                    source = AshSnapshotSource.None,
                    health = health,
                    moduleGate = null,
                ),
            ),
        ).also { lastRefreshFailedAt = System.currentTimeMillis() }
    }

    suspend fun prepareModuleInstall(mode: AshInstallMode): AshModuleInstaller.PreparedInstall =
        moduleInstaller.prepare(mode)

    fun releaseRootSession() {
        repository.releaseRootConnection()
    }

    suspend fun setSetting(key: String, value: String): OperationResult = writable {
        repository.setSetting(key, value)
    }

    suspend fun setSettings(values: Map<String, String>): OperationResult = writable {
        repository.setSettings(values)
    }

    suspend fun setTrust(folder: String, trust: String): OperationResult = writable {
        repository.setTrust(folder, trust)
    }

    suspend fun restoreOne(folder: String): OperationResult =
        executeRecoveryPlan(AshRecoveryPlanEngine.custom(requireCurrentSnapshot(), listOf(folder)))

    suspend fun restoreHalf(): OperationResult {
        val snapshot = requireCurrentSnapshot()
        val plan = AshRecoveryPlanEngine.presets(snapshot)
            .firstOrNull { it.preset == AshRecoveryPlanPreset.Balanced }
            ?: AshRecoveryPlanEngine.presets(snapshot).first()
        return executeRecoveryPlan(plan)
    }

    suspend fun restoreBatch(folders: List<String>): OperationResult =
        executeRecoveryPlan(AshRecoveryPlanEngine.custom(requireCurrentSnapshot(), folders))

    suspend fun restoreAll(): OperationResult {
        val snapshot = requireCurrentSnapshot()
        val plan = AshRecoveryPlanEngine.presets(snapshot)
            .firstOrNull { it.preset == AshRecoveryPlanPreset.Rapid }
            ?: AshRecoveryPlanEngine.custom(snapshot, snapshot.quarantine.map { it.folder })
        return executeRecoveryPlan(plan)
    }

    suspend fun executeRecoveryPlan(plan: AshRecoveryPlan): OperationResult {
        val current = refresh()
        check(current.source == AshSnapshotSource.Live && !current.readOnly && current.lifecycle.compatible) {
            "Live compatible AshReXcue access is required for this action"
        }
        val snapshot = requireNotNull(current.snapshot) { "Refresh AshReXcue before executing a recovery plan" }
        val blockers = AshRecoveryPlanEngine.executionGuards(plan, snapshot)
            .filter { it.severity == AshRecoveryGuardSeverity.Blocker }
        check(blockers.isEmpty()) {
            blockers.joinToString(" ") { guard -> "${guard.title}: ${guard.detail}" }
        }
        return repository.executeRecoveryPlan(plan)
    }
    suspend fun completeTrial(): OperationResult = writable(repository::completeTrial)
    suspend fun rollbackTrial(): OperationResult = writable(repository::rollbackTrial)
    suspend fun discardPending(): OperationResult = writable(repository::discardPending)
    suspend fun exportDiagnostics(): OperationResult = writable(repository::exportDiagnostics)
    suspend fun repairState(): OperationResult = writable(repository::repairState)
    suspend fun recordGuidanceOutcome(
        recommendationId: String,
        moduleFolder: String,
        outcome: AshGuidanceOutcome,
    ): OperationResult = repository.recordGuidanceOutcome(recommendationId, moduleFolder, outcome)

    private fun requireCurrentSnapshot() = requireNotNull(_state.value.snapshot) {
        "Refresh AshReXcue before creating a recovery plan"
    }

    private suspend fun writable(block: suspend () -> OperationResult): OperationResult {
        val current = _state.value
        check(current.source == AshSnapshotSource.Live && !current.readOnly && current.lifecycle.compatible) {
            "Live compatible AshReXcue access is required for this action"
        }
        return block()
    }

    private fun publish(state: AshManagerState): AshManagerState =
        state.also {
            lastRefreshCompletedAt = System.currentTimeMillis()
            if (state.source == AshSnapshotSource.Live) lastRefreshFailedAt = 0L
            _state.value = it
        }

    private data class LiveRefresh(
        val snapshotRaw: String,
        val snapshot: com.dergoogler.mmrl.ash.model.AshSnapshot,
        val lifecycle: com.dergoogler.mmrl.ash.model.AshModuleLifecycle,
        val moduleGate: com.dergoogler.mmrl.ash.model.AshModuleReleaseGate?,
    )

    private companion object {
        const val FAILURE_BACKOFF_MILLIS = 15_000L
    }
}

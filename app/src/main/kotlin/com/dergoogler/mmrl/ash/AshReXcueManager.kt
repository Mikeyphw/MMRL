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
import com.dergoogler.mmrl.ash.model.AshSnapshotSource
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
                    Triple(snapshotRaw, snapshot, lifecycle)
                }
                live.onSuccess { (snapshotRaw, snapshot, lifecycle) ->
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
                        ),
                    )
                }.onFailure { error ->
                    liveError = error.message ?: "Unable to read the AshReXcue snapshot"
                }
            }
        }

        val cached = snapshotStore.read()
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
                AshManagerState(
                    rootAvailable = rootAvailable,
                    lifecycle = lifecycle,
                    snapshot = snapshot,
                    source = AshSnapshotSource.Cache,
                    readOnly = true,
                    lastSuccessfulAt = cached.savedAt,
                    liveError = liveError ?: "Live AshReXcue status is unavailable",
                )
            }.getOrNull()
            if (cachedState != null) return@withLock publish(cachedState)
        }

        val installation = currentInstallation ?: AshModuleInstallation()
        publish(
            AshManagerState(
                rootAvailable = rootAvailable,
                lifecycle = AshModuleLifecycleResolver.resolve(
                    installation = installation,
                    bundled = bundled,
                    capabilities = null,
                    liveError = liveError,
                ),
                source = AshSnapshotSource.None,
                readOnly = true,
                liveError = liveError,
            ),
        )
    }

    suspend fun prepareModuleInstall(mode: AshInstallMode): AshModuleInstaller.PreparedInstall =
        moduleInstaller.prepare(mode)

    suspend fun setSetting(key: String, value: String): OperationResult = writable {
        repository.setSetting(key, value)
    }

    suspend fun setSettings(values: Map<String, String>): OperationResult = writable {
        repository.setSettings(values)
    }

    suspend fun setTrust(folder: String, trust: String): OperationResult = writable {
        repository.setTrust(folder, trust)
    }

    suspend fun restoreOne(folder: String): OperationResult = writable {
        repository.restoreOne(folder)
    }

    suspend fun restoreHalf(): OperationResult = writable(repository::restoreHalf)
    suspend fun restoreBatch(folders: List<String>): OperationResult = writable {
        require(folders.isNotEmpty()) { "No restoration modules were supplied" }
        if (folders.size == 1) {
            repository.restoreOne(folders.single())
        } else {
            check(_state.value.snapshot?.capabilities?.supports("guided-recovery") == true) {
                "Update AshReXcue before starting an explicit guided batch"
            }
            repository.restoreBatch(folders)
        }
    }
    suspend fun restoreAll(): OperationResult = writable(repository::restoreAll)
    suspend fun completeTrial(): OperationResult = writable(repository::completeTrial)
    suspend fun rollbackTrial(): OperationResult = writable(repository::rollbackTrial)
    suspend fun discardPending(): OperationResult = writable(repository::discardPending)
    suspend fun exportDiagnostics(): OperationResult = writable(repository::exportDiagnostics)
    suspend fun recordGuidanceOutcome(
        recommendationId: String,
        moduleFolder: String,
        outcome: AshGuidanceOutcome,
    ): OperationResult = repository.recordGuidanceOutcome(recommendationId, moduleFolder, outcome)

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
            _state.value = it
        }
}

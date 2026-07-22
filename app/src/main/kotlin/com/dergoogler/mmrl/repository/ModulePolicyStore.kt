package com.dergoogler.mmrl.repository

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.local.ModuleSnapshot
import com.dergoogler.mmrl.model.local.ModuleSnapshotItem
import com.dergoogler.mmrl.model.local.ModuleVersionPolicy
import com.dergoogler.mmrl.model.local.ModuleVersionPolicyMode
import com.dergoogler.mmrl.model.local.State
import com.dergoogler.mmrl.platform.Platform
import com.dergoogler.mmrl.platform.content.LocalModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ModulePolicyStore(context: Context) {
    private val root = File(context.filesDir, "module-governor").apply { mkdirs() }
    private val policiesFile = AtomicFile(File(root, "version-policies.json"))
    private val snapshotsFile = AtomicFile(File(root, "snapshots.json"))
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val policiesFlow = MutableStateFlow(loadPolicies())
    val policies = policiesFlow.asStateFlow()

    private val snapshotsFlow = MutableStateFlow(loadSnapshots())
    val snapshots = snapshotsFlow.asStateFlow()

    suspend fun setPolicy(policy: ModuleVersionPolicy) = withContext(Dispatchers.IO) {
        val normalizedId = ModuleIdentity.normalize(policy.moduleId)
        val updated = policiesFlow.value.toMutableMap()
        if (policy.mode == ModuleVersionPolicyMode.FOLLOW_LATEST) {
            updated.remove(normalizedId)
        } else {
            updated[normalizedId] = policy.copy(moduleId = normalizedId)
        }
        persistPolicies(updated)
        policiesFlow.value = updated.toMap()
    }

    suspend fun saveSnapshot(
        label: String,
        modules: List<LocalModule>,
        platform: Platform,
        policies: Map<String, ModuleVersionPolicy> = policiesFlow.value,
        ashTrustStates: Map<String, String> = emptyMap(),
    ): ModuleSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val snapshot = ModuleSnapshot(
            id = UUID.randomUUID().toString(),
            label = label.ifBlank { "Known-good modules" },
            createdAt = now,
            manager = platform.displayName(),
            androidSdk = Build.VERSION.SDK_INT,
            metadataOnly = true,
            cachedZipCount = 0,
            modules = modules.map { module ->
                val normalizedId = ModuleIdentity.normalize(module.id.id)
                ModuleSnapshotItem(
                    id = normalizedId,
                    name = module.name,
                    version = module.version,
                    versionCode = module.versionCode,
                    author = module.author,
                    description = module.description,
                    enabled = module.state == State.ENABLE || module.state == State.UPDATE,
                    state = module.state.name,
                    size = module.size,
                    lastUpdated = module.lastUpdated,
                    policy = policies[normalizedId],
                    ashTrustState = ashTrustStates[normalizedId],
                )
            }.sortedBy { it.name.lowercase() },
        )
        val updated = listOf(snapshot) + snapshotsFlow.value.filterNot { it.id == snapshot.id }
        val trimmed = updated.take(MAX_SNAPSHOTS)
        persistSnapshots(trimmed)
        snapshotsFlow.value = trimmed
        snapshot
    }

    suspend fun deleteSnapshot(id: String) = withContext(Dispatchers.IO) {
        val updated = snapshotsFlow.value.filterNot { it.id == id }
        persistSnapshots(updated)
        snapshotsFlow.value = updated
    }

    private fun loadPolicies(): Map<String, ModuleVersionPolicy> = runCatching {
        if (!policiesFile.baseFile.isFile) return emptyMap()
        val envelope = json.decodeFromString(PolicyEnvelope.serializer(), policiesFile.readFully().toString(Charsets.UTF_8))
        envelope.policies.associateBy { ModuleIdentity.normalize(it.moduleId) }
    }.getOrElse { emptyMap() }

    private fun loadSnapshots(): List<ModuleSnapshot> = runCatching {
        if (!snapshotsFile.baseFile.isFile) return emptyList()
        val envelope = json.decodeFromString(SnapshotEnvelope.serializer(), snapshotsFile.readFully().toString(Charsets.UTF_8))
        envelope.snapshots.sortedByDescending { it.createdAt }.take(MAX_SNAPSHOTS)
    }.getOrElse { emptyList() }

    private fun persistPolicies(policies: Map<String, ModuleVersionPolicy>) {
        writeAtomic(
            policiesFile,
            json.encodeToString(
                PolicyEnvelope.serializer(),
                PolicyEnvelope(policies = policies.values.sortedBy { it.moduleId }),
            ).toByteArray(Charsets.UTF_8),
        )
    }

    private fun persistSnapshots(snapshots: List<ModuleSnapshot>) {
        writeAtomic(
            snapshotsFile,
            json.encodeToString(
                SnapshotEnvelope.serializer(),
                SnapshotEnvelope(snapshots = snapshots.sortedByDescending { it.createdAt }.take(MAX_SNAPSHOTS)),
            ).toByteArray(Charsets.UTF_8),
        )
    }

    private fun writeAtomic(
        file: AtomicFile,
        payload: ByteArray,
    ) {
        val stream = file.startWrite()
        try {
            stream.write(payload)
            stream.flush()
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    @Serializable
    private data class PolicyEnvelope(
        val version: Int = 1,
        val policies: List<ModuleVersionPolicy> = emptyList(),
    )

    @Serializable
    private data class SnapshotEnvelope(
        val version: Int = 1,
        val snapshots: List<ModuleSnapshot> = emptyList(),
    )

    private companion object {
        const val MAX_SNAPSHOTS = 12
    }
}

fun Platform.displayName(): String = when (this) {
    Platform.KsuNext -> "KernelSU Next"
    Platform.KernelSU -> "KernelSU"
    Platform.Magisk -> "Magisk"
    Platform.APatch -> "APatch"
    Platform.MKSU -> "MKSU"
    Platform.SukiSU -> "SukiSU Ultra"
    Platform.RKSU -> "RKSU"
    Platform.Shizuku -> "Shizuku"
    Platform.NonRoot -> "Non-root"
    Platform.Unknown -> "Unknown"
}

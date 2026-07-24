package com.dergoogler.mmrl.lsposed

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class LsposedPolicyStore(context: Context) {
    private val root = File(context.filesDir, "lsposed-governor").apply { mkdirs() }
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

    suspend fun setPolicy(policy: LsposedVersionPolicy) = withContext(Dispatchers.IO) {
        val normalizedPackage = LsposedIdentity.normalize(policy.packageName)
        val updated = policiesFlow.value.toMutableMap()
        if (policy.mode == LsposedVersionPolicyMode.FOLLOW_LATEST) {
            updated.remove(normalizedPackage)
        } else {
            updated[normalizedPackage] = policy.copy(packageName = normalizedPackage)
        }
        persistPolicies(updated)
        policiesFlow.value = updated.toMap()
    }

    suspend fun saveSnapshot(
        label: String,
        modules: List<LsposedInstalledModule>,
        policies: Map<String, LsposedVersionPolicy> = policiesFlow.value,
    ): LsposedSnapshot = withContext(Dispatchers.IO) {
        val snapshot = LsposedSnapshot(
            id = UUID.randomUUID().toString(),
            label = label.ifBlank { "Known-good LSPosed APK modules" },
            createdAt = System.currentTimeMillis(),
            metadataOnly = true,
            modules = modules.map { module ->
                val normalizedPackage = LsposedIdentity.normalize(module.packageName)
                module.toSnapshotItem(policy = policies[normalizedPackage])
            }.sortedBy { it.name.lowercase() },
        )
        val updated = (listOf(snapshot) + snapshotsFlow.value.filterNot { it.id == snapshot.id }).take(MAX_SNAPSHOTS)
        persistSnapshots(updated)
        snapshotsFlow.value = updated
        snapshot
    }

    suspend fun deleteSnapshot(id: String) = withContext(Dispatchers.IO) {
        val updated = snapshotsFlow.value.filterNot { it.id == id }
        persistSnapshots(updated)
        snapshotsFlow.value = updated
    }

    private fun loadPolicies(): Map<String, LsposedVersionPolicy> = runCatching {
        if (!policiesFile.baseFile.isFile) return emptyMap()
        val envelope = json.decodeFromString(PolicyEnvelope.serializer(), policiesFile.readFully().toString(Charsets.UTF_8))
        envelope.policies.associateBy { LsposedIdentity.normalize(it.packageName) }
    }.getOrElse { emptyMap() }

    private fun loadSnapshots(): List<LsposedSnapshot> = runCatching {
        if (!snapshotsFile.baseFile.isFile) return emptyList()
        val envelope = json.decodeFromString(SnapshotEnvelope.serializer(), snapshotsFile.readFully().toString(Charsets.UTF_8))
        envelope.snapshots.sortedByDescending { it.createdAt }.take(MAX_SNAPSHOTS)
    }.getOrElse { emptyList() }

    private fun persistPolicies(policies: Map<String, LsposedVersionPolicy>) {
        writeAtomic(
            policiesFile,
            json.encodeToString(
                PolicyEnvelope.serializer(),
                PolicyEnvelope(policies = policies.values.sortedBy { it.packageName }),
            ).toByteArray(Charsets.UTF_8),
        )
    }

    private fun persistSnapshots(snapshots: List<LsposedSnapshot>) {
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
        val policies: List<LsposedVersionPolicy> = emptyList(),
    )

    @Serializable
    private data class SnapshotEnvelope(
        val version: Int = 1,
        val snapshots: List<LsposedSnapshot> = emptyList(),
    )

    private companion object {
        const val MAX_SNAPSHOTS = 12
    }
}

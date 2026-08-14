package com.dergoogler.mmrl.installer

import com.dergoogler.mmrl.platform.model.ModId
import java.io.File
import java.security.MessageDigest

/** Identity selected by MMRL and verified against the module archive before root execution. */
data class ArtifactIdentity(
    val moduleId: ModId,
    val expectedModuleId: ModId? = null,
) {
    init {
        moduleId.requireOperational()
        expectedModuleId?.requireOperational()
        require(expectedModuleId == null || moduleId == expectedModuleId) {
            "Archive module ID ${moduleId.id} does not match expected module ID ${expectedModuleId?.id}"
        }
    }
}

/** Digest/size captured from the archive that passed inspection. */
data class ReviewedArtifactIdentity(
    val identity: ArtifactIdentity,
    val sha256: String,
    val sizeBytes: Long,
) {
    init {
        require(SHA256_PATTERN.matches(sha256)) { "Invalid inspected SHA-256" }
        require(sizeBytes > 0L) { "Reviewed archive is empty" }
    }

    companion object {
        private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}

object InstallIdentityPolicy {
    fun expectedModuleIds(
        values: List<String>,
        artifactCount: Int,
    ): List<ModId?> {
        require(artifactCount >= 0) { "Invalid artifact count" }
        if (values.isEmpty()) return List(artifactCount) { null }
        require(values.size == artifactCount) {
            "Expected module identity count does not match selected archive count"
        }
        return values.map { value ->
            ModId.parseOrNull(value)
                ?: throw IllegalArgumentException("Invalid expected module ID: $value")
        }
    }

    fun verify(
        actual: ModId,
        expected: ModId?,
    ): ArtifactIdentity = ArtifactIdentity(actual, expected)

    fun verifyInspectedModule(
        identity: ArtifactIdentity,
        actual: ModId,
    ): ArtifactIdentity {
        require(actual == identity.moduleId) {
            "Archive module ID changed between preflight and inspected bytes: ${identity.moduleId.id} -> ${actual.id}"
        }
        require(identity.expectedModuleId == null || actual == identity.expectedModuleId) {
            "Inspected archive module ID ${actual.id} does not match expected module ID ${identity.expectedModuleId?.id}"
        }
        return identity
    }

    fun bindInspection(
        identity: ArtifactIdentity,
        file: File,
        sha256: String,
    ): ReviewedArtifactIdentity {
        require(file.isFile && file.canRead()) { "Reviewed archive is no longer readable" }
        return ReviewedArtifactIdentity(
            identity = identity,
            sha256 = sha256.lowercase(),
            sizeBytes = file.length(),
        )
    }

    /** Recheck the inspected archive immediately before privileged command construction. */
    fun verifyUnchanged(
        reviewed: ReviewedArtifactIdentity,
        file: File,
    ) {
        require(file.isFile && file.canRead()) { "Reviewed archive is no longer readable" }
        require(file.length() == reviewed.sizeBytes) { "Reviewed archive size changed before installation" }
        require(sha256(file) == reviewed.sha256) { "Reviewed archive contents changed before installation" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

package com.dergoogler.mmrl.installer

import com.dergoogler.mmrl.platform.model.ModId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ArtifactIdentityTest {
    @Test
    fun selectedIdentityMustMatchArchiveIdentity() {
        val expected = ModId("module_a")
        val identity = InstallIdentityPolicy.verify(ModId("module_a"), expected)
        assertEquals("module_a", identity.moduleId.id)
    }


    @Test(expected = IllegalArgumentException::class)
    fun emptySentinelCannotBecomeArtifactAuthority() {
        ArtifactIdentity(ModId.EMPTY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedArchiveIdentityIsRejected() {
        InstallIdentityPolicy.verify(ModId("archive_b"), ModId("selected_a"))
    }

    @Test
    fun externalArchiveCanBeParsedWithoutCallerSuppliedIdentity() {
        val expected = InstallIdentityPolicy.expectedModuleIds(emptyList(), 1)
        assertNull(expected.single())
    }

    @Test(expected = IllegalArgumentException::class)
    fun expectedIdentityCountMustMatchArchiveCount() {
        InstallIdentityPolicy.expectedModuleIds(listOf("one", "two"), 1)
    }

    @Test
    fun reviewedArchiveMustRemainByteIdenticalBeforeCommandConstruction() {
        val archive = File.createTempFile("mmrl-o1-artifact", ".zip")
        try {
            archive.writeText("reviewed bytes")
            val identity = InstallIdentityPolicy.verify(ModId("module_a"), ModId("module_a"))
            val reviewed =
                InstallIdentityPolicy.bindInspection(
                    identity = identity,
                    file = archive,
                    sha256 = sha256(archive),
                )

            InstallIdentityPolicy.verifyUnchanged(reviewed, archive)

            archive.writeText("changed bytes")
            runCatching {
                InstallIdentityPolicy.verifyUnchanged(reviewed, archive)
            }.onSuccess {
                throw AssertionError("Changed archive must be rejected")
            }
        } finally {
            archive.delete()
        }
    }


    @Test(expected = IllegalArgumentException::class)
    fun inspectedBytesCannotChangeModuleIdentityAfterPreflight() {
        val selected = InstallIdentityPolicy.verify(ModId("module_a"), ModId("module_a"))
        InstallIdentityPolicy.verifyInspectedModule(selected, ModId("module_b"))
    }

    @Test
    fun inspectedBytesRetainExactCanonicalModuleIdentity() {
        val selected = InstallIdentityPolicy.verify(ModId("Module_A"), ModId("Module_A"))
        assertEquals(
            "Module_A",
            InstallIdentityPolicy.verifyInspectedModule(selected, ModId("Module_A")).moduleId.id,
        )
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(file.readBytes()).joinToString("") { byte -> "%02x".format(byte) }
    }
}

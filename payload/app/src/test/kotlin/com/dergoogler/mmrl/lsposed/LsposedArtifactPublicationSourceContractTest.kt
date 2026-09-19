package com.dergoogler.mmrl.lsposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LsposedArtifactPublicationSourceContractTest {
    @Test fun apkMaterializationIsAttemptScopedContainedAndAtomic() {
        val source = File("src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt").readText()
        assertTrue(source.contains("UUID.randomUUID()"))
        assertTrue(source.contains(".part"))
        assertTrue(source.contains("containedCacheFile"))
        assertTrue(source.contains("StandardCopyOption.ATOMIC_MOVE"))
        assertTrue(source.contains("Files.deleteIfExists(temporary.toPath())"))
        assertTrue(source.contains("finally"))
        assertTrue(source.contains("verifyDownloadedApk(temporary"))
        assertFalse(source.contains("safeName.replace(Regex"))
    }
}

package com.dergoogler.mmrl.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OperationStoragePolicyTest {
    private val repositoryRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/kotlin").isDirectory }
    }

    private fun productionSource(relative: String): String = File(repositoryRoot, relative).readText()
    @Test fun `operation ids become opaque single path components`() {
        val safe = OperationStoragePolicy.safeOperationId("op/../child")
        assertTrue(safe.matches(Regex("op-[0-9a-f]{64}")))
        assertFalse(safe.contains("child"))
        assertFalse(safe.contains('/'))
    }

    @Test fun `replacement sanitizer collisions remain distinct`() {
        assertNotEquals(
            OperationStoragePolicy.safeOperationId("a/b"),
            OperationStoragePolicy.safeOperationId("a?b"),
        )
    }

    @Test fun `opaque mapping is deterministic`() {
        assertEquals(
            OperationStoragePolicy.safeOperationId("same-operation"),
            OperationStoragePolicy.safeOperationId("same-operation"),
        )
    }

    @Test fun `blank whitespace dot and dotdot operation ids are rejected`() {
        listOf("", " ", "\t", ".", "..").forEach { value ->
            assertTrue(runCatching { OperationStoragePolicy.safeOperationId(value) }.isFailure)
        }
    }

    @Test fun `absolute and separator shaped ids stay opaque and contained`() {
        val root = Files.createTempDirectory("operation-store-shaped-id").toFile()
        try {
            listOf("/tmp/escape", "\\server\\share", "a/b", "a\\b").forEach { operationId ->
                val child = OperationStoragePolicy.operationDirectory(root, operationId)
                assertEquals(root.canonicalFile, child.parentFile)
                assertTrue(child.name.matches(Regex("op-[0-9a-f]{64}")))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `direct child verification rejects nested descendants`() {
        val base = Files.createTempDirectory("operation-store-nested").toFile()
        try {
            val root = File(base, "root").apply { mkdirs() }
            val nested = File(root, "first/second").apply { mkdirs() }
            assertTrue(runCatching { OperationStoragePolicy.requireDirectChild(root, nested) }.isFailure)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun `relative traversal text is opaque and contained`() {
        val root = Files.createTempDirectory("operation-store").toFile()
        try {
            val child = OperationStoragePolicy.operationDirectory(root, "../x")
            assertEquals(root.canonicalFile, child.parentFile)
            assertTrue(child.name.startsWith("op-"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `symlinked operation child fails closed`() {
        val base = Files.createTempDirectory("operation-store-symlink").toFile()
        try {
            val root = File(base, "root").apply { mkdirs() }
            val outside = File(base, "outside").apply { mkdirs() }
            val childName = OperationStoragePolicy.safeOperationId("linked-operation")
            val link = File(root, childName).toPath()
            val linked = runCatching { Files.createSymbolicLink(link, outside.toPath()) }.isSuccess
            if (linked) {
                assertTrue(runCatching {
                    OperationStoragePolicy.operationDirectory(root, "linked-operation")
                }.isFailure)
                assertTrue(runCatching {
                    OperationStoragePolicy.requireDirectChild(root, File(root, childName))
                }.isFailure)
            }
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun `direct child verification rejects outside paths`() {
        val base = Files.createTempDirectory("operation-store-containment").toFile()
        try {
            val root = File(base, "root").apply { mkdirs() }
            val outside = File(base, "outside").apply { mkdirs() }
            assertTrue(runCatching { OperationStoragePolicy.requireDirectChild(root, outside) }.isFailure)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun `quota check fails closed on overflow and excess`() {
        assertTrue(OperationStoragePolicy.canFit(40, 2, 42))
        assertFalse(OperationStoragePolicy.canFit(41, 2, 42))
        assertFalse(OperationStoragePolicy.canFit(Long.MAX_VALUE, 1, Long.MAX_VALUE))
    }

    @Test fun `age retention expires only older staging`() {
        assertFalse(OperationStoragePolicy.isExpired(900, 1_000, 100))
        assertTrue(OperationStoragePolicy.isExpired(899, 1_000, 100))
    }

    @Test fun `active staging lease cannot be pruned while unrelated staging can`() {
        val leased = setOf("active-op")
        assertFalse(OperationStoragePolicy.canPruneOperation("active-op", leased))
        assertTrue(OperationStoragePolicy.canPruneOperation("finished-op", leased))
    }

    @Test fun `streaming byte counter enforces actual emitted limit`() {
        var total = 0L
        total = OperationStoragePolicy.addWithinLimit(total, 6, 10)
        assertEquals(6L, total)
        total = OperationStoragePolicy.addWithinLimit(total, 4, 10)
        assertEquals(10L, total)
        val failure = runCatching { OperationStoragePolicy.addWithinLimit(total, 1, 10) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test fun `sibling symlink cannot redirect one operation into another`() {
        val base = Files.createTempDirectory("operation-store-sibling-link").toFile()
        try {
            val root = File(base, "root").apply { mkdirs() }
            val victim = OperationStoragePolicy.operationDirectory(root, "victim").apply { mkdir() }
            File(victim, "keep.txt").writeText("keep")
            val attackerName = OperationStoragePolicy.safeOperationId("attacker")
            val link = File(root, attackerName).toPath()
            val linked = runCatching { Files.createSymbolicLink(link, victim.toPath()) }.isSuccess
            if (linked) {
                assertTrue(runCatching {
                    OperationStoragePolicy.operationDirectory(root, "attacker")
                }.isFailure)
                assertTrue(runCatching {
                    OperationStoragePolicy.deleteDirectChild(root, File(root, attackerName))
                }.isFailure)
                assertTrue(File(victim, "keep.txt").isFile)
            }
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun `managed recursive deletion does not follow nested symlinks`() {
        val base = Files.createTempDirectory("operation-store-nested-link-delete").toFile()
        try {
            val root = File(base, "root").apply { mkdirs() }
            val outside = File(base, "outside").apply { mkdirs() }
            val keep = File(outside, "keep.txt").apply { writeText("keep") }
            val operation = OperationStoragePolicy.operationDirectory(root, "operation").apply { mkdir() }
            File(operation, "owned.txt").writeText("owned")
            val linked = runCatching {
                Files.createSymbolicLink(File(operation, "outside-link").toPath(), outside.toPath())
            }.isSuccess
            if (linked) {
                assertTrue(OperationStoragePolicy.deleteDirectChild(root, operation))
                assertFalse(operation.exists())
                assertTrue(keep.isFile)
            }
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun `managed size does not count files reached only through nested symlinks`() {
        val base = Files.createTempDirectory("operation-store-size-link").toFile()
        try {
            val root = File(base, "root").apply { mkdirs() }
            val outside = File(base, "outside").apply { mkdirs() }
            File(outside, "outside.bin").writeBytes(ByteArray(64))
            val operation = OperationStoragePolicy.operationDirectory(root, "operation").apply { mkdir() }
            File(operation, "owned.bin").writeBytes(ByteArray(7))
            val linked = runCatching {
                Files.createSymbolicLink(File(operation, "outside-link").toPath(), outside.toPath())
            }.isSuccess
            if (linked) {
                assertEquals(7L, OperationStoragePolicy.directChildSize(root, operation))
            }
        } finally {
            base.deleteRecursively()
        }
    }


    @Test fun `managed deletion reports whether an operation directory existed`() {
        val root = Files.createTempDirectory("operation-store-delete-result").toFile()
        try {
            val operation = OperationStoragePolicy.operationDirectory(root, "delete-me").apply { mkdir() }
            File(operation, "owned.txt").writeText("owned")
            assertTrue(OperationStoragePolicy.deleteOperationDirectory(root, "delete-me"))
            assertFalse(operation.exists())
            assertFalse(OperationStoragePolicy.deleteOperationDirectory(root, "delete-me"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `invalid deletion id fails before touching store contents`() {
        val root = Files.createTempDirectory("operation-store-invalid-delete").toFile()
        try {
            val keep = File(root, "keep.txt").apply { writeText("keep") }
            assertTrue(runCatching { OperationStoragePolicy.deleteOperationDirectory(root, "..") }.isFailure)
            assertTrue(keep.isFile)
        } finally {
            root.deleteRecursively()
        }
    }


    @Test fun `managed file rejects symlinked operation parent`() {
        val base = Files.createTempDirectory("operation-store-managed-file-parent-link").toFile()
        try {
            val root = File(base, "root").apply { mkdirs() }
            val victim = OperationStoragePolicy.operationDirectory(root, "victim").apply { mkdir() }
            val archive = File(victim, "archive.zip").apply { writeText("data") }
            val attacker = File(root, OperationStoragePolicy.safeOperationId("attacker")).toPath()
            val linked = runCatching { Files.createSymbolicLink(attacker, victim.toPath()) }.isSuccess
            if (linked) {
                val throughLink = File(attacker.toFile(), archive.name)
                assertTrue(runCatching { OperationStoragePolicy.requireManagedFile(root, throughLink) }.isFailure)
                assertTrue(archive.isFile)
            }
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun `managed file rejects file symlink`() {
        val base = Files.createTempDirectory("operation-store-managed-file-link").toFile()
        try {
            val root = File(base, "root").apply { mkdirs() }
            val operation = OperationStoragePolicy.operationDirectory(root, "operation").apply { mkdir() }
            val outside = File(base, "outside.zip").apply { writeText("outside") }
            val link = File(operation, "archive.zip").toPath()
            val linked = runCatching { Files.createSymbolicLink(link, outside.toPath()) }.isSuccess
            if (linked) {
                assertTrue(runCatching { OperationStoragePolicy.requireManagedFile(root, link.toFile()) }.isFailure)
                assertTrue(outside.isFile)
            }
        } finally {
            base.deleteRecursively()
        }
    }


    @Test fun `staging store resolves and deletes only managed operation children`() {
        val text = productionSource("app/src/main/kotlin/com/dergoogler/mmrl/installer/OperationStagingStore.kt")
        assertTrue(text.contains("OperationStoragePolicy.operationDirectory(root, operationId)"))
        assertTrue(text.contains("OperationStoragePolicy.deleteDirectChild(root, opDir)"))
        assertTrue(text.contains("OperationStoragePolicy.directChildSize(root, dir)"))
        assertFalse(text.contains("root.resolve(safeOperationId)"))
        assertFalse(text.contains("opDir.deleteRecursively()"))
    }

    @Test fun `rollback store uses the same containment boundary for creation pruning and deletion`() {
        val text = productionSource("app/src/main/kotlin/com/dergoogler/mmrl/installer/UpdateRollbackStore.kt")
        assertTrue(text.contains("OperationStoragePolicy.operationDirectory(root, operationId)"))
        assertTrue(text.contains("OperationStoragePolicy.deleteDirectChild(root, operationDirectory)"))
        assertTrue(text.contains("OperationStoragePolicy.requireManagedFile(root, candidate)"))
        assertFalse(text.contains("root.resolve(OperationStoragePolicy.safeOperationId(operationId))"))
        assertFalse(text.contains("dir.deleteRecursively()"))
    }

    @Test fun `Tasker review archives use opaque operation directories and safe cleanup`() {
        val tokenStore = productionSource("app/src/main/kotlin/com/dergoogler/mmrl/tasker/TaskerReviewTokens.kt")
        val actions = productionSource("app/src/main/kotlin/com/dergoogler/mmrl/tasker/TaskerReviewedInstallActions.kt")
        assertTrue(tokenStore.contains("OperationStoragePolicy.operationDirectory(root, operationId)"))
        assertTrue(tokenStore.contains("OperationStoragePolicy.deleteOperationDirectory(archiveDirectory(context), operationId)"))
        assertTrue(tokenStore.contains("OperationStoragePolicy.deleteDirectChild(archiveDirectory(context), operationDirectory)"))
        assertFalse(tokenStore.contains("operationId.replace(Regex"))
        assertTrue(actions.contains("TaskerReviewTokenStore.deleteArchiveDirectory(context, operationId)"))
        assertFalse(actions.contains("directory.delete()"))
    }


    @Test fun `Tasker review token metadata uses opaque collision resistant filenames`() {
        val source = productionSource("app/src/main/kotlin/com/dergoogler/mmrl/tasker/TaskerReviewTokens.kt")
        assertTrue(source.contains("OperationStoragePolicy.safeOperationId(token)"))
        assertFalse(source.contains("token.replace(Regex"))
    }

    @Test fun `Tasker token cleanup surfaces filesystem deletion failures`() {
        val source = productionSource("app/src/main/kotlin/com/dergoogler/mmrl/tasker/TaskerReviewTokens.kt")
        assertTrue(source.contains("Files.deleteIfExists(file.toPath())"))
        assertFalse(source.contains("claim.delete()"))
        assertFalse(source.contains("source.delete()"))
    }

}

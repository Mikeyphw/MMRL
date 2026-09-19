package com.dergoogler.mmrl.tasker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskerStorageConvergenceSourceContractTest {
    private fun source(name: String): String = File("src/main/kotlin/com/dergoogler/mmrl/tasker/$name").readText()

    @Test fun rootRequestsUseOpaqueIdsAndCheckedDeletion() {
        val text = source("TaskerAuthorization.kt")
        assertTrue(text.contains("OperationStoragePolicy.safeOperationId(id)"))
        assertTrue(text.contains("Files.deleteIfExists"))
        assertFalse(text.contains("private fun safeId(id: String) = id.replace"))
        assertFalse(text.contains("queuedFile(context, id).delete()"))
    }

    @Test fun reviewTokensPublishAtomicallyAndClaimsAreCreateNew() {
        val text = source("TaskerReviewTokens.kt")
        assertTrue(text.contains("AtomicFile(file(context, value.token))"))
        assertTrue(text.contains("StandardOpenOption.CREATE_NEW"))
        assertTrue(text.contains("Files.deleteIfExists"))
    }

    @Test fun exportedLogsAndEventDeliveryUseOpaqueIdsAndCheckedDeletion() {
        val runtime = source("TaskerRuntime.kt")
        val events = source("TaskerEvents.kt")
        assertTrue(runtime.contains("OperationStoragePolicy.safeOperationId(entry.id)"))
        assertTrue(runtime.contains("Files.deleteIfExists"))
        assertFalse(runtime.contains("entry.id.replace(Regex"))
        assertTrue(events.contains("OperationStoragePolicy.safeOperationId(id)"))
        assertTrue(events.contains("Files.deleteIfExists"))
        assertFalse(events.contains("id.replace(Regex"))
    }
}

package com.dergoogler.mmrl.release

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditRemediationRegressionMatrixContractTest {
    private val root: File by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it, "app/src/test/kotlin").isDirectory }
    }

    private fun testSource(relative: String): String = File(root, relative).readText()

    @Test fun `storage escape and symlink regressions remain covered`() {
        val text = testSource("app/src/test/kotlin/com/dergoogler/mmrl/installer/OperationStoragePolicyTest.kt")
        listOf("separator shaped ids", "symlinked operation child", "nested symlinks", "invalid deletion id").forEach {
            assertTrue("missing storage regression: $it", text.contains(it))
        }
    }

    @Test fun `download race callback and recovery regressions remain covered`() {
        val ownership = testSource("app/src/test/kotlin/com/dergoogler/mmrl/service/DownloadOperationOwnershipTest.kt")
        val destination = testSource("app/src/test/kotlin/com/dergoogler/mmrl/service/DownloadDestinationOwnershipTest.kt")
        val callbacks = testSource("app/src/test/kotlin/com/dergoogler/mmrl/service/DownloadCallbackIsolationTest.kt")
        val journal = testSource("app/src/test/kotlin/com/dergoogler/mmrl/service/DownloadAttemptJournalTest.kt")
        assertTrue(ownership.contains("only one concurrent claim owns a logical operation"))
        val lifecycle = testSource("app/src/test/kotlin/com/dergoogler/mmrl/service/DownloadDurableLifecycleSourceContractTest.kt")
        assertTrue(destination.contains("many simultaneous claims allocate unique destinations"))
        assertTrue(destination.contains("stale lease cannot release a newer reservation"))
        assertTrue(callbacks.contains("every download callback category is exception isolated"))
        assertTrue(journal.contains("concurrent terminal transitions preserve first terminal writer"))
        assertTrue(journal.contains("queued downloading and publishing attempts survive journal recreation"))
        assertTrue(lifecycle.contains("finalization is independent of service lifecycle scope and releases ownership after durable state"))
        assertTrue(lifecycle.contains("startup reconciles every durable non terminal phase"))
    }

    @Test fun `identity deep link network and Tasker regressions remain covered`() {
        val identity = testSource("app/src/test/kotlin/com/dergoogler/mmrl/lsposed/LsposedIdentityConvergenceTest.kt")
        val deepLink = testSource("app/src/test/kotlin/com/dergoogler/mmrl/ui/activity/RepositoryDeepLinkTrustSourceContractTest.kt")
        val network = testSource("app/src/test/kotlin/com/dergoogler/mmrl/network/NetworkRedirectHardeningTest.kt")
        val tasker = testSource("app/src/test/kotlin/com/dergoogler/mmrl/tasker/TaskerStorageConvergenceSourceContractTest.kt")
        assertTrue(identity.contains("normalization_is_locale_stable_and_case_whitespace_insensitive"))
        assertTrue(deepLink.contains("activityRequiresExplicitConfirmationBeforeInsert"))
        assertTrue(deepLink.contains("cancellationCannotPersistProposal"))
        assertTrue(network.contains("carrierGradeNatIpv4IsRejected"))
        assertTrue(network.contains("redirectDowngradeIsRejected"))
        assertTrue(tasker.contains("reviewTokensPublishAtomicallyAndClaimsAreCreateNew"))
    }
}

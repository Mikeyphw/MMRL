package com.dergoogler.mmrl.network

import io.github.mikeyphw.mmrl.shared.GitHubRepository
import io.github.mikeyphw.mmrl.shared.GitHubRunIdentity
import io.github.mikeyphw.mmrl.shared.StableModuleOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM policy tests; no Android device, root, module installation, or network. */
class N05CSharedCoreTest {
    @Test fun firstPartyUrlBindingRejectsForeignOrModifiedAssets() {
        val good = "https://github.com/Mikeyphw/MMRL/releases/download/v1.2.3/app.apk"
        assertTrue(FirstPartyReleaseMetadata.matchesReleaseAsset("v1.2.3", "app.apk", good))
        assertTrue(FirstPartyReleaseMetadata.matchesReleasePage("v1.2.3",
            "https://github.com/Mikeyphw/MMRL/releases/tag/v1.2.3"))
        assertFalse(FirstPartyReleaseMetadata.matchesReleaseAsset("v1.2.3", "app.apk", "$good?x=1"))
        assertFalse(FirstPartyReleaseMetadata.matchesReleaseAsset("v1.2.3", "other.apk", good))
        assertFalse(FirstPartyReleaseMetadata.matchesReleaseAsset("v1.2.3", "app.apk",
            good.replace("Mikeyphw/MMRL/", "Mikeyphw/MMRL-LSPosed/")))
        assertFalse(FirstPartyReleaseMetadata.matchesReleaseAsset("../v1", "app.apk", good))
    }

    @Test fun hashMatchingRequiresPinnedDigest() {
        val good = "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertTrue(FirstPartyReleaseMetadata.verifiesSha256("abc".toByteArray(), good))
        assertFalse(FirstPartyReleaseMetadata.verifiesSha256("abcd".toByteArray(), good))
        assertFalse(FirstPartyReleaseMetadata.verifiesSha256("abc".toByteArray(), "sha256:bad"))
        assertFalse(FirstPartyReleaseMetadata.verifiesSha256("abc".toByteArray(), good.uppercase()))
    }

    @Test fun workflowMetadataRequiresExactRunAttemptAndCommit() {
        val repo = GitHubRepository.parse("Mikeyphw/MMRL")
        assertNotNull(repo)
        val sha = "a".repeat(40)
        val run = GitHubRunIdentity.of(requireNotNull(repo), 100L, 2, sha, "push", "next")
        assertNotNull(run)
        val valid = requireNotNull(run)
        assertTrue(valid.matchesCurrentRun(100L, 2, sha, "push", "next", "completed", "success"))
        assertFalse(valid.matchesCurrentRun(100L, 1, sha, "push", "next", "completed", "success"))
        assertFalse(valid.matchesCurrentRun(100L, 2, sha, "push", "next", "completed", "failure"))
        assertFalse(valid.matchesArtifactOwner(100L, "b".repeat(40)))
    }

    @Test fun stableSortingIsCaseInsensitiveWithIdTieBreak() {
        assertTrue(StableModuleOrder.compare("Alpha", "1", "bravo", "1") < 0)
        assertTrue(StableModuleOrder.compare("ALPHA", "a", "alpha", "b") < 0)
    }
}

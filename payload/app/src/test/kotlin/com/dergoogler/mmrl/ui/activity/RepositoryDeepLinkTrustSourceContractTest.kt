package com.dergoogler.mmrl.ui.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RepositoryDeepLinkTrustSourceContractTest {
    private val activity = File("src/main/kotlin/com/dergoogler/mmrl/ui/activity/MainActivity.kt").readText()
    private val policy = File("src/main/kotlin/com/dergoogler/mmrl/ui/activity/MainLaunchIntentPolicy.kt").readText()

    @Test fun deepLinkProducesProposalInsteadOfPersistenceRequest() {
        assertTrue(policy.contains("RepositoryProposal"))
        assertTrue(policy.contains("normalizedHost"))
        assertFalse(policy.contains("repositoryUrl = validatedRepositoryUrl"))
    }

    @Test fun activityRequiresExplicitConfirmationBeforeInsert() {
        assertTrue(activity.contains("pendingRepositoryProposal"))
        assertTrue(activity.contains("ConfirmDialog("))
        assertTrue(activity.contains("approveRepositoryProposal(proposal)"))
        assertFalse(activity.contains("request.repositoryUrl?.let"))
    }

    @Test fun confirmationShowsNormalizedHostAndUrl() {
        assertTrue(activity.contains("proposal.normalizedHost"))
        assertTrue(activity.contains("proposal.normalizedUrl"))
    }

    @Test fun httpsContainerPathUsesSegmentBoundary() {
        assertTrue(policy.contains("path == \"/repository\" || path.startsWith(\"/repository/\")"))
        assertFalse(policy.contains("uri.path.orEmpty().startsWith(\"/repository\")"))
    }

    @Test fun repositoryProposalRequiresHttpsWithoutCredentialsOrFragments() {
        assertTrue(policy.contains("require(scheme == \"https\")"))
        assertTrue(policy.contains("require(parsed.userInfo == null)"))
        assertTrue(policy.contains("require(parsed.fragment == null)"))
        assertTrue(policy.contains("IDN.toASCII"))
    }

    @Test fun cancellationCannotPersistProposal() {
        assertTrue(activity.contains("onClose = { pendingRepositoryProposal = null }"))
        assertTrue(activity.contains("onDismissRequest = { pendingRepositoryProposal = null }"))
    }
}

package com.dergoogler.mmrl.installer

import org.junit.Test

class InstallExecutionAuthorizationPolicyTest {
    private val digest = "a".repeat(64)

    @Test fun `generic launch may wait for approval before it has a reviewed digest`() {
        InstallExecutionAuthorizationPolicy.requireAuthorizedLaunch(
            artifactCount = 2,
            requireApproval = true,
            reviewedSha256 = emptyList(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `direct privileged launch without one reviewed digest per archive is rejected`() {
        InstallExecutionAuthorizationPolicy.requireAuthorizedLaunch(
            artifactCount = 2,
            requireApproval = false,
            reviewedSha256 = listOf(digest),
        )
    }

    @Test fun `direct launch accepts a complete reviewed digest set`() {
        InstallExecutionAuthorizationPolicy.requireAuthorizedLaunch(
            artifactCount = 2,
            requireApproval = false,
            reviewedSha256 = listOf(digest, "b".repeat(64)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed review digest cannot authorize execution`() {
        InstallExecutionAuthorizationPolicy.requireAuthorizedLaunch(
            artifactCount = 1,
            requireApproval = false,
            reviewedSha256 = listOf("not-a-digest"),
        )
    }
}

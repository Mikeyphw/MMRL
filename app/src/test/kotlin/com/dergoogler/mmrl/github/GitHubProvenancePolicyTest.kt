package com.dergoogler.mmrl.github

import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubProvenancePolicyTest {
    @Test
    fun `nightly candidates carry immutable run artifact and commit provenance`() {
        val candidate = GitHubCandidate(
            id = "artifact-99-7",
            name = "module.zip",
            sourceName = "Build",
            version = "abcdef0",
            versionCode = 99,
            downloadUrl = "https://api.github.com/repos/o/r/actions/artifacts/7/zip",
            apiDownloadUrl = "https://api.github.com/repos/o/r/actions/artifacts/7/zip",
            size = 1234,
            updatedAt = "2026-08-15T00:00:00Z",
            mode = GitHubSourceMode.NIGHTLY,
            score = 1,
            sourceCommit = "abcdef0123456789",
            workflowRunId = 99,
            artifactId = 7,
        )

        val provenance = candidate.provenanceSummary()

        assertTrue(provenance.contains("commit=abcdef0123456789"))
        assertTrue(provenance.contains("run=99"))
        assertTrue(provenance.contains("artifact=7"))
    }
}

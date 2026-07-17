package com.dergoogler.mmrl.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubSourceSpecTest {
    @Test
    fun `release asset api url resolves to release source`() {
        val source = GitHubSourceSpec.fromDownloadUrl(
            "https://api.github.com/repos/Owner/Repo/releases/assets/123",
        )

        assertEquals(GitHubSourceMode.RELEASE, source?.mode)
        assertEquals("https://github.com/Owner/Repo?mmrlSource=release", source?.sourceUrl)
    }

    @Test
    fun `actions artifact api url resolves to nightly source`() {
        val source = GitHubSourceSpec.fromDownloadUrl(
            "https://api.github.com/repos/Owner/Repo/actions/artifacts/456/zip",
        )

        assertEquals(GitHubSourceMode.NIGHTLY, source?.mode)
        assertEquals("https://github.com/Owner/Repo?mmrlSource=nightly", source?.sourceUrl)
    }

    @Test
    fun `unrelated url is ignored`() {
        assertNull(GitHubSourceSpec.fromDownloadUrl("https://example.com/module.zip"))
    }
}

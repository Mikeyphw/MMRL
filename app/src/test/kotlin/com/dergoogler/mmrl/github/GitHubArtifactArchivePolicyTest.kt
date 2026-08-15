package com.dergoogler.mmrl.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubArtifactArchivePolicyTest {
    @Test
    fun `github actions artifact api archive is treated as artifact archive`() {
        assertTrue(
            GitHubArtifactArchivePolicy.isActionsArtifactArchive(
                "https://api.github.com/repos/JingMatrix/Vector/actions/artifacts/123/zip",
            ),
        )
    }

    @Test
    fun `nightly link workflow archive is not treated as artifact archive`() {
        assertFalse(
            GitHubArtifactArchivePolicy.isActionsArtifactArchive(
                "https://nightly.link/JingMatrix/Vector/workflows/core/master/Vector-Release.zip",
            ),
        )
    }

    @Test
    fun `github actions artifact failure message does not mention nightly link`() {
        val message = GitHubArtifactArchivePolicy.downloadFailureMessage(
            url = "https://api.github.com/repos/Owner/Repo/actions/artifacts/456/zip",
            code = 403,
            hasToken = false,
            bodySnippet = "Forbidden",
        )

        assertFalse(message.contains("nightly.link"))
        assertTrue(message.contains("GitHub token"))
    }

    @Test
    fun `regular release zip is not treated as artifact archive`() {
        assertFalse(
            GitHubArtifactArchivePolicy.isActionsArtifactArchive(
                "https://github.com/Owner/Repo/releases/download/v1/module.zip",
            ),
        )
    }

    @Test
    fun `module root detects root module prop`() {
        assertEquals(
            "",
            GitHubArtifactArchivePolicy.moduleRoot(listOf("module.prop", "zygisk/lib.so")),
        )
    }

    @Test
    fun `module root detects wrapped module directory`() {
        assertEquals(
            "Vector-Release/",
            GitHubArtifactArchivePolicy.moduleRoot(
                listOf("Vector-Release/module.prop", "Vector-Release/customize.sh"),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `github artifact materialization is bounded by bytes actually emitted`() {
        GitHubArtifactArchivePolicy.nextMaterializedBytes(
            GitHubArtifactArchivePolicy.MAX_MATERIALIZED_BYTES,
            1,
        )
    }

}

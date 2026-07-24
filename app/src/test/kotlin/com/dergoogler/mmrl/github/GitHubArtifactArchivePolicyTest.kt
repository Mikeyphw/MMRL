package com.dergoogler.mmrl.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubArtifactArchivePolicyTest {
    @Test
    fun `nightly link workflow archive is treated as artifact archive`() {
        assertTrue(
            GitHubArtifactArchivePolicy.isActionsArtifactArchive(
                "https://nightly.link/JingMatrix/Vector/workflows/core/master/Vector-Release.zip",
            ),
        )
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
}

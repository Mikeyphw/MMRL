package com.dergoogler.mmrl.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubSourceRulesContractTest {
    @Test
    fun `saved github source round trips editable nightly rules`() {
        val source = GitHubSourceSpec(
            owner = "JingMatrix",
            repository = "Vector",
            mode = GitHubSourceMode.NIGHTLY,
            artifactRegex = ".*Release.*",
            rejectRegex = ".*(symbols|mapping|debug).*",
            preferredVariantRegex = "zygisk|arm64|release",
            branchRegex = "master|main",
            workflowRegex = "core",
            artifactStrategy = GitHubArtifactStrategy.EXTRACTED_MODULE_LAYOUT,
        )

        val parsed = GitHubSourceSpec.fromSourceUrl(source.sourceUrl)

        assertEquals(GitHubSourceMode.NIGHTLY, parsed?.mode)
        assertEquals(".*Release.*", parsed?.artifactRegex)
        assertEquals(".*(symbols|mapping|debug).*", parsed?.rejectRegex)
        assertEquals("zygisk|arm64|release", parsed?.preferredVariantRegex)
        assertEquals("master|main", parsed?.branchRegex)
        assertEquals("core", parsed?.workflowRegex)
        assertEquals(GitHubArtifactStrategy.EXTRACTED_MODULE_LAYOUT, parsed?.artifactStrategy)
        assertTrue(source.sourceUrl.contains("artifactStrategy=extractedModuleLayout"))
    }

    @Test
    fun `legacy regex remains compatible as generic file matcher`() {
        val parsed = GitHubSourceSpec.fromSourceUrl(
            "https://github.com/PerformanC/ReZygisk?mmrlSource=nightly&regex=release%7Carm64",
        )

        assertEquals(GitHubSourceMode.NIGHTLY, parsed?.mode)
        assertEquals("release|arm64", parsed?.regex)
        assertEquals("https://github.com/PerformanC/ReZygisk?mmrlSource=nightly&regex=release%7Carm64", parsed?.sourceUrl)
    }

    @Test
    fun `strategy query accepts aliases for user edited sources`() {
        assertEquals(GitHubArtifactStrategy.DIRECT_MODULE_ZIP, GitHubArtifactStrategy.fromQuery("direct_zip"))
        assertEquals(GitHubArtifactStrategy.NESTED_ZIP, GitHubArtifactStrategy.fromQuery("nested-module-zip"))
        assertEquals(GitHubArtifactStrategy.SINGLE_FOLDER_MODULE_LAYOUT, GitHubArtifactStrategy.fromQuery("wrapped"))
    }
}

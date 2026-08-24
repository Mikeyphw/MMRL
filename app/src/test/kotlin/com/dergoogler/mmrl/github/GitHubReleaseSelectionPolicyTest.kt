package com.dergoogler.mmrl.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseSelectionPolicyTest {
    private fun release(id: Long, name: String, assets: List<String>, prerelease: Boolean = false) =
        GitHubModuleResolver.GitHubRelease(
            id = id,
            tagName = name,
            prerelease = prerelease,
            assets = assets.mapIndexed { index, asset ->
                GitHubModuleResolver.GitHubAsset(
                    id = id * 100 + index,
                    name = asset,
                    url = "https://api.github.com/assets/${id * 100 + index}",
                )
            },
        )

    @Test
    fun `falls through newest release when its assets do not match source rules`() {
        val selected = GitHubReleaseSelectionPolicy.select(
            releases = listOf(
                release(2, "2.0", listOf("source.zip")),
                release(1, "1.9", listOf("module-release.zip")),
            ),
            includePreReleases = false,
        ) { it.name.startsWith("module-") }

        assertNotNull(selected)
        assertEquals("1.9", selected!!.first.tagName)
        assertEquals(listOf("module-release.zip"), selected.second.map { it.name })
    }

    @Test
    fun `prerelease is ignored unless source allows it`() {
        val releases = listOf(release(2, "2.0-beta", listOf("module.zip"), prerelease = true))
        assertNull(GitHubReleaseSelectionPolicy.select(releases, false) { true })
        assertEquals("2.0-beta", GitHubReleaseSelectionPolicy.select(releases, true) { true }!!.first.tagName)
    }
}

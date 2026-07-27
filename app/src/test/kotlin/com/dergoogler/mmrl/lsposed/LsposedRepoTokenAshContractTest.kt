package com.dergoogler.mmrl.lsposed

import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedRepoTokenAshContractTest {
    private val root = repositoryRoot()

    @Test
    fun `LSPosed repository uses mirror fallback and app wide GitHub token`() {
        val source = root.resolve("app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRepository.kt").toFile().readText()

        assertTrue(source.contains("GitHubTokenStore(context)"))
        assertTrue(source.contains("LSPOSED_MODULES_FALLBACK_URLS"))
        assertTrue(source.contains("cdn.jsdelivr.net/gh/Xposed-Modules-Repo/modules@gh-pages/modules.json"))
        assertTrue(source.contains("lsposedModuleFallbackUrls(packageName)"))
        assertTrue(source.contains("applyGitHubAuthentication(url, githubTokenStore.getToken())"))
        assertTrue(source.contains("HTTP 403 from modules.lsposed.org; mirror fallback will be tried"))
    }

    @Test
    fun `GitHub token setting is app wide and does not store raw token in user preferences proto`() {
        val screen = root.resolve("app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/settings/other/OtherScreen.kt").toFile().readText()
        val strings = root.resolve("app/src/main/res/values/strings.xml").toFile().readText()
        val preferences = root.resolve("datastore/src/main/kotlin/com/dergoogler/mmrl/datastore/model/UserPreferences.kt").toFile().readText()

        assertTrue(screen.contains("GitHubTokenStore(context)"))
        assertTrue(screen.contains("settings_github_api_token"))
        assertTrue(screen.contains("githubTokenEditorRevision++"))
        assertTrue(strings.contains("settings_github_api_token_desc_empty"))
        assertTrue(!preferences.contains("githubApiToken"))
    }

    private fun repositoryRoot(): Path = generateSequence(Paths.get("").toAbsolutePath()) { path -> path.parent }
        .first { path ->
            path.resolve("settings.gradle.kts").toFile().isFile &&
                path.resolve("app/src/main/kotlin/com/dergoogler/mmrl").toFile().isDirectory
        }
}

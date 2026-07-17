package com.dergoogler.mmrl.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositorySourceLoaderTest {
    @Test
    fun `direct JSON repository URL is not rewritten`() {
        val url =
            "https://raw.githubusercontent.com/KernelSU-Next/" +
                "KernelSU-Next-Modules-Repo/refs/heads/main/modules.json"

        assertEquals(url, RepositorySourceLoader.resolveModulesUrl(url))
    }

    @Test
    fun `KernelSU catalog defaults visibility to visible`() {
        val entries =
            RepositorySourceLoader.parseKernelSuCatalog(
                """
                [
                  {
                    "name": "Visible module",
                    "description": "Example",
                    "repoUrl": "https://github.com/example/module"
                  },
                  {
                    "name": "Hidden module",
                    "repoUrl": "https://github.com/example/hidden",
                    "visibility": 0
                  }
                ]
                """.trimIndent(),
            )

        assertEquals(2, entries.size)
        assertEquals(1, entries.first().visibility)
        assertTrue(entries.last().visibility == 0)
    }
}

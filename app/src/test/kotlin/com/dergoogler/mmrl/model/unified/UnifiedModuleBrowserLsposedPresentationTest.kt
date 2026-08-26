package com.dergoogler.mmrl.model.unified

import com.dergoogler.mmrl.lsposed.LsposedRepoModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedModuleBrowserLsposedPresentationTest {
    @Test
    fun `LSPosed row does not swap repository title and summary`() {
        val module = LsposedRepoModule(
            name = "io.github.example.module",
            description = "Example Module",
            summary = "Short repository summary",
        )

        val item = UnifiedModuleBrowserModel.build(
            UnifiedModuleInputs(lsposedRepositoryModules = listOf(module)),
        ).single()

        assertEquals("Example Module", item.title)
        assertEquals("Short repository summary", item.description)
        assertTrue(item.searchTokens.contains("example module"))
        assertTrue(item.searchTokens.contains("short repository summary"))
    }
}

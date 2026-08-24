package com.dergoogler.mmrl.lsposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LsposedRepositoryPresentationContractTest {
    @Test
    fun `repository description is title and SUMMARY is tile description`() {
        val module = LsposedRepoModule(
            name = "io.github.example.module",
            description = "Example Module",
            summary = "Changes system UI behavior without replacing APKs.",
        )

        assertEquals("Example Module", module.displayName)
        assertEquals("Changes system UI behavior without replacing APKs.", module.displayDescription)
    }

    @Test
    fun `readme fallback skips markdown title instead of duplicating module title`() {
        val module = LsposedRepoModule(
            name = "io.github.example.module",
            description = "Example Module",
            readme = "# Example Module\n\nA detailed module description.",
        )

        assertEquals("Example Module", module.displayName)
        assertEquals("A detailed module description.", module.displayDescription)
    }

    @Test
    fun `index rejects duplicate package identities`() {
        val duplicate = listOf(
            LsposedRepoModule(name = "io.github.Example", description = "One"),
            LsposedRepoModule(name = "IO.GITHUB.EXAMPLE", description = "Two"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            LsposedRepositoryIndexPolicy.validate(duplicate)
        }
    }
}

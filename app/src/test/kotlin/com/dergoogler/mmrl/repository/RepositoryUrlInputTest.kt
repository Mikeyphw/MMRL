package com.dergoogler.mmrl.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RepositoryUrlInputTest {
    @Test
    fun `preserves KernelSU Next raw manifest URL`() {
        val input =
            "https://raw.githubusercontent.com/KernelSU-Next/" +
                "KernelSU-Next-Modules-Repo/refs/heads/main/modules.json"

        assertEquals(input, normalizeRepositoryUrlInput(input))
    }

    @Test
    fun `adds HTTPS to a repository host`() {
        assertEquals(
            "https://gr.dergoogler.com/gmr/",
            normalizeRepositoryUrlInput("gr.dergoogler.com/gmr/"),
        )
    }

    @Test
    fun `rejects plain HTTP repositories`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRepositoryUrlInput("http://example.com/repository")
        }
    }
}

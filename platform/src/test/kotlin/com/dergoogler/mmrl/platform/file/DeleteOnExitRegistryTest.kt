package com.dergoogler.mmrl.platform.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteOnExitRegistryTest {
    @Test
    fun registrationDoesNotDeleteUntilShutdownDrain() {
        val registry = DeleteOnExitRegistry()
        val deleted = mutableListOf<String>()

        assertTrue(registry.register(DeleteOnExitRegistry.Entry("/data/adb", "parent")))
        assertTrue(registry.register(DeleteOnExitRegistry.Entry("/data/adb", "parent/child")))
        assertEquals(emptyList<String>(), deleted)

        assertTrue(registry.drain { entry -> deleted += entry.relative; true })
        assertEquals(listOf("parent/child", "parent"), deleted)
        assertEquals(emptyList<DeleteOnExitRegistry.Entry>(), registry.snapshot())
    }
}

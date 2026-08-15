package com.dergoogler.mmrl.installer

import org.junit.Assert.assertEquals
import org.junit.Test

class DependencyPlanPolicyTest {
    @Test
    fun `transitive dependencies are ordered before requested module`() {
        val nodes = mapOf(
            "root" to DependencyPlanPolicy.Node("root", setOf("dep-b", "dep-a")),
            "dep-a" to DependencyPlanPolicy.Node("dep-a", setOf("leaf")),
            "dep-b" to DependencyPlanPolicy.Node("dep-b"),
            "leaf" to DependencyPlanPolicy.Node("leaf"),
        )
        assertEquals(listOf("leaf", "dep-a", "dep-b", "root"), DependencyPlanPolicy.plan(listOf("root"), nodes).orderedIds)
    }

    @Test
    fun `already installed dependency participates in graph but is not downloaded`() {
        val nodes = mapOf(
            "root" to DependencyPlanPolicy.Node("root", setOf("installed")),
            "installed" to DependencyPlanPolicy.Node("installed", alreadyInstalled = true),
        )
        assertEquals(listOf("root"), DependencyPlanPolicy.plan(listOf("root"), nodes).orderedIds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dependency cycle is rejected`() {
        DependencyPlanPolicy.plan(
            listOf("a"),
            mapOf(
                "a" to DependencyPlanPolicy.Node("a", setOf("b")),
                "b" to DependencyPlanPolicy.Node("b", setOf("a")),
            ),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `missing dependency is rejected`() {
        DependencyPlanPolicy.plan(listOf("a"), mapOf("a" to DependencyPlanPolicy.Node("a", setOf("missing"))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `incompatible dependency is rejected`() {
        DependencyPlanPolicy.plan(
            listOf("a"),
            mapOf(
                "a" to DependencyPlanPolicy.Node("a", setOf("b")),
                "b" to DependencyPlanPolicy.Node("b", compatible = false),
            ),
        )
    }
}

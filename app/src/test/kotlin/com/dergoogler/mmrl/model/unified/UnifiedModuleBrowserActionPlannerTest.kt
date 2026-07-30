package com.dergoogler.mmrl.model.unified

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedModuleBrowserActionPlannerTest {
    @Test
    fun `problem action becomes safe clipboard evidence action`() {
        val problem = UnifiedModuleProblem(
            id = "problem:repo",
            kind = UnifiedModuleProblemKind.INSTALLED_NOT_IN_REPOSITORY,
            severity = UnifiedBadgeSeverity.WARNING,
            title = "Installed module is not linked",
            summary = "No repository evidence was found.",
            moduleId = "example",
            evidence = listOf(UnifiedProblemEvidence("Source", "missing")),
        )

        val action = UnifiedModuleBrowserActionPlanner.forProblem(
            problem = problem,
            action = UnifiedProblemAction(UnifiedProblemActionKind.COPY_EVIDENCE),
        )
        val result = UnifiedModuleBrowserActionPlanner.resultFor(action)

        assertEquals(UnifiedModuleBrowserActionKind.COPY_EVIDENCE, action.kind)
        assertFalse(action.destructive)
        assertTrue(action.clipboardText.contains("Installed module is not linked"))
        assertTrue(action.clipboardText.contains("Source: missing"))
        assertTrue(result.handled)
        assertTrue(result.safe)
        assertTrue(result.copiedText?.contains("example") == true)
    }

    @Test
    fun `github source row exposes edit and source copy actions without mutation`() {
        val item = item(
            id = "githubish",
            sourceTypes = setOf(UnifiedModuleSourceType.GITHUB_SOURCE, UnifiedModuleSourceType.REPOSITORY),
            sourceUrl = "https://github.com/example/module",
        )

        val actions = UnifiedModuleBrowserActionPlanner.forItem(item)

        assertTrue(actions.any { it.kind == UnifiedModuleBrowserActionKind.COPY_SOURCE_URL })
        assertTrue(actions.any { it.kind == UnifiedModuleBrowserActionKind.OPEN_GITHUB_SOURCE_RULES })
        assertTrue(actions.any { it.kind == UnifiedModuleBrowserActionKind.REFRESH_REPOSITORY })
        assertTrue(actions.none { it.destructive })
        assertEquals(
            "https://github.com/example/module",
            actions.first { it.kind == UnifiedModuleBrowserActionKind.COPY_SOURCE_URL }.clipboardText,
        )
    }

    @Test
    fun `provider and probe actions are safe refresh style results`() {
        val refresh = UnifiedModuleBrowserAction(
            kind = UnifiedModuleBrowserActionKind.REFRESH_PROVIDER,
            moduleId = "lsposed",
            moduleTitle = "LSPosed",
        )
        val probe = UnifiedModuleBrowserAction(
            kind = UnifiedModuleBrowserActionKind.RUN_DEBUG_PROBE,
            moduleId = "lsposed",
            moduleTitle = "LSPosed",
        )

        assertTrue(UnifiedModuleBrowserActionPlanner.resultFor(refresh).handled)
        assertTrue(UnifiedModuleBrowserActionPlanner.resultFor(probe).handled)
        assertTrue(UnifiedModuleBrowserActionPlanner.resultFor(refresh).safe)
        assertTrue(UnifiedModuleBrowserActionPlanner.resultFor(probe).message.contains("LSPosed"))
    }

    private fun item(
        id: String,
        title: String = id,
        sourceTypes: Set<UnifiedModuleSourceType> = setOf(UnifiedModuleSourceType.REPOSITORY),
        sourceUrl: String? = null,
        installState: UnifiedInstallState = UnifiedInstallState.AVAILABLE,
        providerCompatibility: UnifiedProviderCompatibility = UnifiedProviderCompatibility.NOT_APPLICABLE,
        scope: UnifiedScopeState = UnifiedScopeState.None,
        rescue: UnifiedRescueState = UnifiedRescueState.None,
        badges: List<UnifiedModuleBadge> = emptyList(),
    ): UnifiedModuleItem = UnifiedModuleItem(
        canonicalId = id,
        displayId = id,
        title = title,
        subtitle = installState.label,
        description = "Description for $title",
        author = "Tester",
        sourceTypes = sourceTypes,
        sourceMode = if (sourceTypes.contains(UnifiedModuleSourceType.GITHUB_SOURCE)) {
            UnifiedModuleSourceMode.NIGHTLY
        } else {
            UnifiedModuleSourceMode.REPOSITORY
        },
        sourceUrl = sourceUrl,
        repositoryName = "Test repo",
        artifactStrategy = null,
        aliases = emptySet(),
        state = UnifiedModuleState(
            installState = installState,
            providerCompatibility = providerCompatibility,
            scope = scope,
            rescue = rescue,
        ),
        match = UnifiedModuleMatch(
            reason = UnifiedMatchReason.EXACT_ID,
            confidence = 100,
            explanation = "Synthetic exact match.",
            matchedValues = setOf(id),
        ),
        badges = badges,
        searchTokens = setOf(id, title),
    )
}

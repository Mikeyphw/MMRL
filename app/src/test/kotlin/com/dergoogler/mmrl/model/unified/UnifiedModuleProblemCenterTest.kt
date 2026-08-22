package com.dergoogler.mmrl.model.unified

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedModuleProblemCenterTest {
    @Test
    fun `installed module without source becomes actionable repository problem`() {
        val report = UnifiedModuleProblemCenter.build(
            listOf(
                item(
                    id = "orphan",
                    installState = UnifiedInstallState.INSTALLED,
                    sourceTypes = setOf(UnifiedModuleSourceType.INSTALLED_ROOT),
                ),
            ),
        )

        val problem = report.problems.single { it.kind == UnifiedModuleProblemKind.INSTALLED_NOT_IN_REPOSITORY }
        assertEquals(UnifiedBadgeSeverity.WARNING, problem.severity)
        assertTrue(problem.actions.any { it.kind == UnifiedProblemActionKind.OPEN_MODULE })
        assertTrue(problem.actions.any { it.kind == UnifiedProblemActionKind.COPY_EVIDENCE })
        assertFalse(report.healthy)
    }

    @Test
    fun `alias matches are surfaced as low severity review notes`() {
        val report = UnifiedModuleProblemCenter.build(
            listOf(
                item(
                    id = "example-module",
                    match = UnifiedModuleMatch(
                        reason = UnifiedMatchReason.ALIAS_ID,
                        confidence = 86,
                        explanation = "Matched through a legacy module alias.",
                        matchedValues = setOf("legacy_module", "example-module"),
                    ),
                    aliases = setOf("legacy_module", "example-module"),
                ),
            ),
        )

        val problem = report.problems.single { it.kind == UnifiedModuleProblemKind.ALIAS_MATCH_ONLY }
        assertEquals(UnifiedBadgeSeverity.INFO, problem.severity)
        assertTrue(problem.evidence.any { it.value.contains("legacy_module") })
        assertTrue(report.healthy)
    }

    @Test
    fun `external repo and github signals keep roadmap problem vocabulary`() {
        val report = UnifiedModuleProblemCenter.build(
            items = emptyList(),
            signals = listOf(
                UnifiedProblemSignal(
                    kind = UnifiedModuleProblemKind.PRIMARY_REPO_403,
                    severity = UnifiedBadgeSeverity.ERROR,
                    title = "Primary repo blocked",
                    summary = "Repository returned HTTP 403.",
                    sourceLabel = "LSPosed backup index",
                    evidence = listOf(UnifiedProblemEvidence("HTTP", "403")),
                ),
                UnifiedProblemSignal(
                    kind = UnifiedModuleProblemKind.GITHUB_TOKEN_REQUIRED,
                    severity = UnifiedBadgeSeverity.WARNING,
                    title = "GitHub token required",
                    summary = "Actions artifacts need a readable token.",
                    sourceLabel = "owner/repo",
                ),
                UnifiedProblemSignal(
                    kind = UnifiedModuleProblemKind.GITHUB_ARTIFACT_EXPIRED,
                    severity = UnifiedBadgeSeverity.WARNING,
                    title = "Nightly artifact expired",
                    summary = "The saved artifact download is no longer available.",
                    sourceLabel = "owner/repo",
                ),
            ),
        )

        assertEquals(3, report.total)
        assertEquals(1, report.errors)
        assertEquals(2, report.warnings)
        assertTrue(report.problems.any { it.kind == UnifiedModuleProblemKind.PRIMARY_REPO_403 })
        assertTrue(report.problems.any { problem ->
            problem.kind == UnifiedModuleProblemKind.GITHUB_TOKEN_REQUIRED &&
                problem.actions.any { it.kind == UnifiedProblemActionKind.EDIT_GITHUB_SOURCE }
        })
    }

    @Test
    fun `problem text filter searches evidence actions and severity`() {
        val report = UnifiedModuleProblemCenter.build(
            listOf(
                item(
                    id = "scopey",
                    sourceTypes = setOf(UnifiedModuleSourceType.LSPOSED_INSTALLED),
                    providerCompatibility = UnifiedProviderCompatibility.UNAVAILABLE,
                ),
            ),
        )

        assertTrue(UnifiedModuleProblemCenter.filter(report, "scope").problems.isNotEmpty())
        assertTrue(UnifiedModuleProblemCenter.filter(report, "run probe").problems.isNotEmpty())
        assertTrue(UnifiedModuleProblemCenter.filter(report, "error").problems.isNotEmpty())
        assertTrue(UnifiedModuleProblemCenter.filter(report, "definitely-missing").problems.isEmpty())
    }

    private fun item(
        id: String,
        title: String = id,
        sourceTypes: Set<UnifiedModuleSourceType> = setOf(UnifiedModuleSourceType.REPOSITORY),
        installState: UnifiedInstallState = UnifiedInstallState.AVAILABLE,
        providerCompatibility: UnifiedProviderCompatibility = UnifiedProviderCompatibility.NOT_APPLICABLE,
        scope: UnifiedScopeState = UnifiedScopeState.None,
        match: UnifiedModuleMatch = UnifiedModuleMatch(
            reason = UnifiedMatchReason.EXACT_ID,
            confidence = 100,
            explanation = "Synthetic exact match.",
            matchedValues = setOf(id),
        ),
        aliases: Set<String> = emptySet(),
        badges: List<UnifiedModuleBadge> = emptyList(),
    ): UnifiedModuleItem = UnifiedModuleItem(
        canonicalId = id,
        displayId = id,
        title = title,
        subtitle = installState.label,
        description = "Description for $title",
        author = "Tester",
        sourceTypes = sourceTypes,
        sourceMode = if (sourceTypes.contains(UnifiedModuleSourceType.INSTALLED_ROOT)) {
            UnifiedModuleSourceMode.INSTALLED
        } else {
            UnifiedModuleSourceMode.REPOSITORY
        },
        sourceUrl = null,
        repositoryName = null,
        artifactStrategy = null,
        aliases = aliases,
        state = UnifiedModuleState(
            installState = installState,
            providerCompatibility = providerCompatibility,
            scope = scope,
        ),
        match = match,
        badges = listOf(
            UnifiedModuleBadge(
                kind = UnifiedBadgeKind.INSTALL_STATE,
                label = installState.label,
                severity = if (installState == UnifiedInstallState.DISABLED) {
                    UnifiedBadgeSeverity.WARNING
                } else {
                    UnifiedBadgeSeverity.INFO
                },
            ),
        ) + badges,
        searchTokens = setOf(id, title),
    )
}

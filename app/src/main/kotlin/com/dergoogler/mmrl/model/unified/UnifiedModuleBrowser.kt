package com.dergoogler.mmrl.model.unified

import com.dergoogler.mmrl.ash.model.AshManagerState
import com.dergoogler.mmrl.ash.model.AshModuleProtection
import com.dergoogler.mmrl.ash.model.AshModuleRiskBand
import com.dergoogler.mmrl.ash.model.moduleProtections
import com.dergoogler.mmrl.database.entity.local.LocalModuleSource
import com.dergoogler.mmrl.github.GitHubArtifactStrategy
import com.dergoogler.mmrl.github.GitHubSourceMode
import com.dergoogler.mmrl.github.GitHubSourceSpec
import com.dergoogler.mmrl.lsposed.LsposedInstalledModule
import com.dergoogler.mmrl.lsposed.LsposedProviderStatus
import com.dergoogler.mmrl.lsposed.LsposedRepoModule
import com.dergoogler.mmrl.model.ModuleIdentity
import com.dergoogler.mmrl.model.local.LocalModule
import com.dergoogler.mmrl.model.local.State
import com.dergoogler.mmrl.model.local.versionDisplay
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.platform.content.ModuleCompatibility
import java.util.Locale

/**
 * Phase 10 canonical model for the future Modules/Repos/LSPosed/Rescue browser.
 *
 * This layer deliberately contains no Compose UI. It turns the different module-ish
 * sources in the app into stable rows with badges, match explanations, and search
 * tokens so Phase 11 can add filtering, sorting, density, and a new visual shell
 * without re-learning every provider's vocabulary.
 */
data class UnifiedModuleInputs(
    val rootModules: List<LocalModule> = emptyList(),
    val repositoryModules: List<OnlineModule> = emptyList(),
    val savedSources: List<LocalModuleSource> = emptyList(),
    val lsposedRepositoryModules: List<LsposedRepoModule> = emptyList(),
    val lsposedInstalledModules: List<LsposedInstalledModule> = emptyList(),
    val ashState: AshManagerState = AshManagerState(),
    val rootCompatibility: ModuleCompatibility = ModuleCompatibility(
        hasMagicMount = false,
        canRestoreModules = false,
    ),
    val lsposedProviderStatus: LsposedProviderStatus = LsposedProviderStatus(),
    val updateCandidates: Map<String, UnifiedModuleUpdate> = emptyMap(),
    val updateAllowed: Map<String, Boolean> = emptyMap(),
    val lockedUpdates: Set<String> = emptySet(),
    val repositoryNames: Map<String, String> = emptyMap(),
)

data class UnifiedModuleUpdate(
    val installedVersion: String,
    val installedVersionCode: Long,
    val availableVersion: String,
    val availableVersionCode: Long,
    val sourceLabel: String? = null,
    val compatible: Boolean = true,
)

data class UnifiedModuleItem(
    val canonicalId: String,
    val displayId: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val author: String? = null,
    val sourceTypes: Set<UnifiedModuleSourceType>,
    val sourceMode: UnifiedModuleSourceMode,
    val sourceUrl: String? = null,
    val repositoryName: String? = null,
    val artifactStrategy: GitHubArtifactStrategy? = null,
    val aliases: Set<String> = emptySet(),
    val state: UnifiedModuleState,
    val match: UnifiedModuleMatch,
    val badges: List<UnifiedModuleBadge>,
    val searchTokens: Set<String>,
    val sort: UnifiedModuleSortKeys = UnifiedModuleSortKeys(),
) {
    val installed: Boolean get() = state.installState.installed
    val updateAvailable: Boolean get() = state.installState == UnifiedInstallState.UPDATE_AVAILABLE
    val hasProblems: Boolean get() = badges.any { it.severity >= UnifiedBadgeSeverity.WARNING }
}

data class UnifiedModuleState(
    val installState: UnifiedInstallState = UnifiedInstallState.AVAILABLE,
    val enabled: Boolean? = null,
    val installedVersion: String? = null,
    val installedVersionCode: Long? = null,
    val availableVersion: String? = null,
    val availableVersionCode: Long? = null,
    val providerCompatibility: UnifiedProviderCompatibility = UnifiedProviderCompatibility.NOT_APPLICABLE,
    val scope: UnifiedScopeState = UnifiedScopeState.None,
    val rescue: UnifiedRescueState = UnifiedRescueState.None,
)

enum class UnifiedInstallState(val installed: Boolean, val label: String) {
    AVAILABLE(false, "Available"),
    INSTALLED(true, "Installed"),
    DISABLED(true, "Disabled"),
    REMOVE_PENDING(true, "Removal pending"),
    UPDATE_PENDING(true, "Update pending"),
    UPDATE_AVAILABLE(true, "Update available"),
    IGNORED(true, "Updates ignored"),
    LOCKED(true, "Version locked"),
    PROBLEM(false, "Problem"),
    UNKNOWN(false, "Unknown"),
}

enum class UnifiedModuleSourceType(val label: String) {
    INSTALLED_ROOT("Installed root module"),
    REPOSITORY("Repository"),
    GITHUB_SOURCE("Saved GitHub source"),
    LSPOSED_REPOSITORY("LSPosed repository"),
    LSPOSED_INSTALLED("Installed LSPosed APK"),
    LOCAL_FILE("Local module"),
    RESCUE("AshReXcue rescue"),
}

enum class UnifiedModuleSourceMode(val label: String) {
    INSTALLED("Installed"),
    REPOSITORY("Repository"),
    RELEASE("Release"),
    NIGHTLY("Nightly"),
    MANUAL("Manual"),
    LOCAL("Local"),
    RESCUE("Rescue"),
    MIXED("Mixed"),
    UNKNOWN("Unknown"),
}

enum class UnifiedProviderCompatibility(val label: String) {
    COMPATIBLE("Compatible"),
    LIMITED("Limited"),
    UNAVAILABLE("Unavailable"),
    UNKNOWN("Unknown"),
    NOT_APPLICABLE("Not applicable"),
}

sealed interface UnifiedScopeState {
    data object None : UnifiedScopeState
    data class Lsposed(
        val enabled: Boolean,
        val autoInclude: Boolean,
        val scopedPackageCount: Int,
        val packages: List<String>,
    ) : UnifiedScopeState
}

sealed interface UnifiedRescueState {
    data object None : UnifiedRescueState
    data class AshReXcue(
        val folder: String,
        val trust: String,
        val quarantined: Boolean,
        val changedSinceStable: Boolean,
        val riskBand: AshModuleRiskBand,
        val summary: String,
    ) : UnifiedRescueState
}

enum class UnifiedBadgeKind {
    PROVIDER_COMPATIBILITY,
    ARTIFACT_STRATEGY,
    SOURCE_MODE,
    INSTALL_STATE,
    UPDATE,
    SCOPE,
    RESCUE,
    PROBLEM,
}

enum class UnifiedBadgeSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

data class UnifiedModuleBadge(
    val kind: UnifiedBadgeKind,
    val label: String,
    val detail: String = "",
    val severity: UnifiedBadgeSeverity = UnifiedBadgeSeverity.INFO,
)

enum class UnifiedMatchReason {
    EXACT_ID,
    ALIAS_ID,
    PACKAGE_NAME,
    FOLDER_NAME,
    SOURCE_URL,
    UNMATCHED,
}

data class UnifiedModuleMatch(
    val reason: UnifiedMatchReason,
    val confidence: Int,
    val explanation: String,
    val matchedValues: Set<String> = emptySet(),
)

data class UnifiedModuleSortKeys(
    val name: String = "",
    val installedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val versionCode: Long = 0L,
    val scopeCount: Int = 0,
    val problemSeverity: UnifiedBadgeSeverity = UnifiedBadgeSeverity.INFO,
)

data class UnifiedModuleQuery(
    val text: String = "",
    val sourceTypes: Set<UnifiedModuleSourceType> = emptySet(),
    val sourceModes: Set<UnifiedModuleSourceMode> = emptySet(),
    val installStates: Set<UnifiedInstallState> = emptySet(),
    val providerStates: Set<UnifiedProviderCompatibility> = emptySet(),
    val updatesOnly: Boolean = false,
    val problemsOnly: Boolean = false,
)

enum class UnifiedModuleSortMode {
    INSTALLED_FIRST,
    UPDATE_AVAILABLE_FIRST,
    PROBLEM_SEVERITY,
    RECENTLY_UPDATED,
    RECENTLY_INSTALLED,
    MOST_SCOPED_APPS,
    PROVIDER_COMPATIBILITY,
    NAME_A_Z,
}

data class UnifiedAliasGroup(
    val canonicalId: String,
    val aliases: Set<String>,
)

object UnifiedModuleAliasRegistry {
    private val explicitGroups = listOf(
        UnifiedAliasGroup(
            canonicalId = "ashrexcue",
            aliases = setOf(
                "ashlooper",
                "ashrexcue",
                "ashrexcuebootloopprotector",
                "ashrexcue-bootloop-protector",
            ),
        ),
        UnifiedAliasGroup(
            canonicalId = "lsposed",
            aliases = setOf(
                "lsposed",
                "zygisk_lsposed",
                "zygisk-lsposed",
                "riru_lsposed",
                "riru-lsposed",
                "zygisk_next_lsposed",
                "zygisk-next-lsposed",
            ),
        ),
        UnifiedAliasGroup(
            canonicalId = "zygisk_vector",
            aliases = setOf(
                "zygisk_vector",
                "zygisk-vector",
                "vector",
                "matrix_vector",
                "org.matrix.vector.manager",
                "org.matrix.vector.daemon",
            ),
        ),
        UnifiedAliasGroup(
            canonicalId = "rezygisk",
            aliases = setOf("rezygisk", "re-zygisk", "zygisk_re", "zygisk-re"),
        ),
    )

    private val aliasToCanonical: Map<String, String> = explicitGroups
        .flatMap { group ->
            group.aliases
                .flatMap { alias -> listOf(normalize(alias), token(alias)) }
                .filter(String::isNotBlank)
                .map { alias -> alias to group.canonicalId }
        }.toMap()

    private val canonicalToAliases: Map<String, Set<String>> = explicitGroups
        .associate { group -> group.canonicalId to group.aliases.flatMap { listOf(normalize(it), token(it)) }.toSet() }

    fun canonicalFor(raw: String?): String {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return "unknown"
        return aliasToCanonical[normalized]
            ?: aliasToCanonical[token(normalized)]
            ?: ModuleIdentity.canonical(normalized)
    }

    fun aliasesFor(raw: String?): Set<String> {
        val canonical = canonicalFor(raw)
        return (canonicalToAliases[canonical].orEmpty() + ModuleIdentity.aliasesFor(canonical) + canonical)
            .filter(String::isNotBlank)
            .toSet()
    }

    fun match(left: String?, right: String?): UnifiedModuleMatch {
        val leftNormalized = normalize(left)
        val rightNormalized = normalize(right)
        val leftCanonical = canonicalFor(leftNormalized)
        val rightCanonical = canonicalFor(rightNormalized)
        return when {
            leftNormalized.isBlank() || rightNormalized.isBlank() ->
                UnifiedModuleMatch(UnifiedMatchReason.UNMATCHED, 0, "One side had no usable module identity.")
            leftNormalized == rightNormalized ->
                UnifiedModuleMatch(UnifiedMatchReason.EXACT_ID, 100, "Exact normalized module id match.", setOf(leftNormalized))
            leftCanonical == rightCanonical ->
                UnifiedModuleMatch(
                    reason = UnifiedMatchReason.ALIAS_ID,
                    confidence = 86,
                    explanation = "Matched through the unified alias registry as $leftCanonical.",
                    matchedValues = setOf(leftNormalized, rightNormalized, leftCanonical),
                )
            else -> UnifiedModuleMatch(
                reason = UnifiedMatchReason.UNMATCHED,
                confidence = 0,
                explanation = "No id, alias, package, folder, or source match was found.",
            )
        }
    }

    private fun normalize(raw: String?): String = raw.orEmpty().trim().lowercase(Locale.ROOT)
    private fun token(raw: String): String = normalize(raw).filter(Char::isLetterOrDigit)
}

object UnifiedModuleBrowserModel {
    fun build(inputs: UnifiedModuleInputs): List<UnifiedModuleItem> {
        val builder = UnifiedModuleCollectionBuilder(inputs)
        inputs.repositoryModules.forEach(builder::addRepositoryModule)
        inputs.savedSources.forEach(builder::addSavedSource)
        inputs.rootModules.forEach(builder::addRootModule)
        inputs.lsposedRepositoryModules.forEach(builder::addLsposedRepositoryModule)
        inputs.lsposedInstalledModules.forEach(builder::addLsposedInstalledModule)
        builder.addAshRescueState(inputs.ashState)
        return builder.build()
    }

    fun applyQuery(
        items: List<UnifiedModuleItem>,
        query: UnifiedModuleQuery,
    ): List<UnifiedModuleItem> {
        val text = query.text.trim().lowercase(Locale.ROOT)
        return items.filter { item ->
            (query.sourceTypes.isEmpty() || item.sourceTypes.any(query.sourceTypes::contains)) &&
                (query.sourceModes.isEmpty() || item.sourceMode in query.sourceModes) &&
                (query.installStates.isEmpty() || item.state.installState in query.installStates) &&
                (query.providerStates.isEmpty() || item.state.providerCompatibility in query.providerStates) &&
                (!query.updatesOnly || item.updateAvailable) &&
                (!query.problemsOnly || item.hasProblems) &&
                (text.isBlank() || item.searchTokens.any { it.contains(text, ignoreCase = true) })
        }
    }

    fun sort(
        items: List<UnifiedModuleItem>,
        mode: UnifiedModuleSortMode,
        descending: Boolean = false,
    ): List<UnifiedModuleItem> {
        val comparator = when (mode) {
            UnifiedModuleSortMode.INSTALLED_FIRST -> compareByDescending<UnifiedModuleItem> { it.installed }
                .thenBy { it.sort.name }
            UnifiedModuleSortMode.UPDATE_AVAILABLE_FIRST -> compareByDescending<UnifiedModuleItem> { it.updateAvailable }
                .thenBy { it.sort.name }
            UnifiedModuleSortMode.PROBLEM_SEVERITY -> compareByDescending<UnifiedModuleItem> { it.sort.problemSeverity.ordinal }
                .thenBy { it.sort.name }
            UnifiedModuleSortMode.RECENTLY_UPDATED -> compareByDescending<UnifiedModuleItem> { it.sort.updatedAt }
                .thenBy { it.sort.name }
            UnifiedModuleSortMode.RECENTLY_INSTALLED -> compareByDescending<UnifiedModuleItem> { it.sort.installedAt }
                .thenBy { it.sort.name }
            UnifiedModuleSortMode.MOST_SCOPED_APPS -> compareByDescending<UnifiedModuleItem> { it.sort.scopeCount }
                .thenBy { it.sort.name }
            UnifiedModuleSortMode.PROVIDER_COMPATIBILITY -> compareBy<UnifiedModuleItem> { it.state.providerCompatibility.ordinal }
                .thenBy { it.sort.name }
            UnifiedModuleSortMode.NAME_A_Z -> compareBy { it.sort.name }
        }
        val sorted = items.sortedWith(comparator)
        return if (descending) sorted.asReversed() else sorted
    }
}

private class UnifiedModuleCollectionBuilder(
    private val inputs: UnifiedModuleInputs,
) {
    private val entries = linkedMapOf<String, MutableUnifiedModule>()
    private val ashProtections = inputs.ashState.moduleProtections()

    fun addRootModule(module: LocalModule) {
        val canonicalId = UnifiedModuleAliasRegistry.canonicalFor(module.id.id)
        val entry = entry(canonicalId, module.id.id)
        entry.title = entry.title.ifBlank { module.name.ifBlank { module.id.id } }
        entry.subtitle = module.versionDisplay
        entry.description = entry.description.ifBlank { module.description }
        entry.author = entry.author ?: module.author.takeIf(String::isNotBlank)
        entry.sourceTypes += UnifiedModuleSourceType.INSTALLED_ROOT
        entry.sourceMode = mergeMode(entry.sourceMode, UnifiedModuleSourceMode.INSTALLED)
        entry.installedVersion = module.versionDisplay
        entry.installedVersionCode = module.versionCode.toLong()
        entry.enabled = module.state == State.ENABLE || module.state == State.UPDATE
        entry.installState = rootInstallState(module.state)
        entry.updatedAt = maxOf(entry.updatedAt, module.lastUpdated)
        entry.installedAt = maxOf(entry.installedAt, module.lastUpdated)
        entry.searchTokens += listOf(module.id.id, module.name, module.author, module.description)
        entry.match = bestMatch(entry.match, UnifiedModuleMatch(UnifiedMatchReason.EXACT_ID, 100, "Installed root module id is the canonical anchor.", setOf(module.id.id)))
        val repositoryVersionCode = entry.availableVersionCode
        if (repositoryVersionCode != null && repositoryVersionCode > module.versionCode.toLong()) {
            entry.markUpdate(
                availableVersion = entry.availableVersion.orEmpty(),
                availableVersionCode = repositoryVersionCode,
                sourceLabel = entry.repositoryName ?: "Repository",
                compatible = true,
            )
        }
        ashProtections[canonicalId]?.let { entry.applyProtection(it) }
        maybeApplyUpdate(entry, canonicalId, module.versionDisplay, module.versionCode.toLong())
        addRootProviderBadge(entry)
    }

    fun addRepositoryModule(module: OnlineModule) {
        val canonicalId = UnifiedModuleAliasRegistry.canonicalFor(module.id)
        val entry = entry(canonicalId, module.id)
        entry.title = chooseTitle(entry.title, module.name, module.id)
        entry.subtitle = chooseSubtitle(entry.subtitle, module.versionDisplay)
        entry.description = chooseDescription(entry.description, module.description.orEmpty())
        entry.author = entry.author ?: module.author.takeIf(String::isNotBlank)
        entry.sourceTypes += UnifiedModuleSourceType.REPOSITORY
        entry.sourceMode = mergeMode(entry.sourceMode, UnifiedModuleSourceMode.REPOSITORY)
        entry.sourceUrl = entry.sourceUrl ?: module.repoUrl.takeIf(String::isNotBlank)
        entry.repositoryName = entry.repositoryName ?: inputs.repositoryNames[module.repoUrl]
        entry.availableVersion = module.versionDisplay
        entry.availableVersionCode = module.versionCode.toLong()
        entry.searchTokens += listOf(
            module.id,
            module.name,
            module.author,
            module.description.orEmpty(),
            module.repoUrl,
            inputs.repositoryNames[module.repoUrl].orEmpty(),
        ) + module.categories.orEmpty()
        if (!entry.installState.installed) {
            entry.installState = UnifiedInstallState.AVAILABLE
        }
        if (entry.installedVersionCode != null && module.versionCode.toLong() > (entry.installedVersionCode ?: Long.MIN_VALUE)) {
            entry.markUpdate(
                availableVersion = module.versionDisplay,
                availableVersionCode = module.versionCode.toLong(),
                sourceLabel = inputs.repositoryNames[module.repoUrl] ?: "Repository",
                compatible = true,
            )
        }
        entry.match = bestMatch(entry.match, UnifiedModuleAliasRegistry.match(module.id, canonicalId))
    }

    fun addSavedSource(source: LocalModuleSource) {
        val canonicalId = UnifiedModuleAliasRegistry.canonicalFor(source.id)
        val entry = entry(canonicalId, source.id)
        val spec = GitHubSourceSpec.fromSourceUrl(source.sourceUrl)
        entry.sourceTypes += if (spec != null) UnifiedModuleSourceType.GITHUB_SOURCE else UnifiedModuleSourceType.LOCAL_FILE
        entry.sourceMode = mergeMode(entry.sourceMode, spec?.mode.toUnifiedMode(source.mode))
        entry.sourceUrl = entry.sourceUrl ?: source.sourceUrl.takeIf(String::isNotBlank) ?: source.repoUrl.takeIf(String::isNotBlank)
        entry.repositoryName = entry.repositoryName ?: spec?.let { "${it.owner}/${it.repository}" }
        entry.artifactStrategy = entry.artifactStrategy ?: spec?.artifactStrategy
        entry.installedVersion = entry.installedVersion ?: source.installedVersion.takeIf(String::isNotBlank)
        entry.installedVersionCode = entry.installedVersionCode ?: source.installedVersionCode.toLong()
        entry.searchTokens += listOf(
            source.id,
            source.repoUrl,
            source.sourceUrl,
            source.mode,
            spec?.owner.orEmpty(),
            spec?.repository.orEmpty(),
        )
        if (!entry.installState.installed) entry.installState = UnifiedInstallState.INSTALLED
        if (spec == null && source.sourceUrl.isNotBlank()) {
            entry.badges += UnifiedModuleBadge(
                kind = UnifiedBadgeKind.PROBLEM,
                label = "Source needs review",
                detail = "Saved module source is not a recognized GitHub source URL.",
                severity = UnifiedBadgeSeverity.WARNING,
            )
        } else if (spec != null) {
            entry.badges += UnifiedModuleBadge(
                kind = UnifiedBadgeKind.SOURCE_MODE,
                label = spec.mode.toUnifiedMode(source.mode).label,
                detail = "Saved GitHub source ${spec.owner}/${spec.repository}",
                severity = UnifiedBadgeSeverity.INFO,
            )
            if (spec.artifactStrategy != GitHubArtifactStrategy.AUTO) {
                entry.badges += UnifiedModuleBadge(
                    kind = UnifiedBadgeKind.ARTIFACT_STRATEGY,
                    label = spec.artifactStrategy.badgeLabel(),
                    detail = "GitHub artifact strategy ${spec.artifactStrategy.queryValue}",
                    severity = UnifiedBadgeSeverity.INFO,
                )
            }
        }
        entry.match = bestMatch(entry.match, UnifiedModuleMatch(UnifiedMatchReason.SOURCE_URL, 78, "Saved source maps this installed id to ${entry.sourceUrl}.", setOf(source.id, source.sourceUrl)))
    }

    fun addLsposedRepositoryModule(module: LsposedRepoModule) {
        val canonicalId = UnifiedModuleAliasRegistry.canonicalFor(module.packageName)
        val entry = entry(canonicalId, module.packageName)
        entry.title = chooseTitle(entry.title, module.displayName, module.packageName)
        entry.subtitle = chooseSubtitle(entry.subtitle, module.latestStableVersion?.display ?: module.latestRelease.orEmpty())
        entry.description = chooseDescription(entry.description, module.displayDescription)
        entry.sourceTypes += UnifiedModuleSourceType.LSPOSED_REPOSITORY
        entry.sourceMode = mergeMode(entry.sourceMode, UnifiedModuleSourceMode.REPOSITORY)
        entry.sourceUrl = entry.sourceUrl ?: module.sourceUrl ?: module.homepageUrl ?: module.url
        entry.availableVersion = module.latestStableVersion?.display ?: module.latestRelease
        entry.availableVersionCode = module.latestStableVersion?.versionCode
        entry.searchTokens += listOf(
            module.packageName,
            module.displayName,
            module.displayDescription,
            module.sourceUrl.orEmpty(),
            module.homepageUrl.orEmpty(),
            module.url.orEmpty(),
        ) + module.scope
        entry.providerCompatibility = lsposedProviderCompatibility()
        addLsposedProviderBadge(entry)
        if (!entry.installState.installed) entry.installState = UnifiedInstallState.AVAILABLE
        entry.match = bestMatch(entry.match, UnifiedModuleMatch(UnifiedMatchReason.PACKAGE_NAME, 92, "LSPosed repository package name is the canonical anchor.", setOf(module.packageName)))
    }

    fun addLsposedInstalledModule(module: LsposedInstalledModule) {
        val canonicalId = UnifiedModuleAliasRegistry.canonicalFor(module.packageName)
        val entry = entry(canonicalId, module.packageName)
        entry.title = chooseTitle(entry.title, module.displayName, module.packageName)
        entry.subtitle = chooseSubtitle(entry.subtitle, module.installedVersionName ?: module.installedVersionCode.toString())
        entry.description = chooseDescription(entry.description, module.description)
        entry.sourceTypes += UnifiedModuleSourceType.LSPOSED_INSTALLED
        entry.sourceMode = mergeMode(entry.sourceMode, UnifiedModuleSourceMode.INSTALLED)
        entry.installedVersion = module.installedVersionName ?: module.installedVersionCode.toString()
        entry.installedVersionCode = module.installedVersionCode
        entry.availableVersion = module.repoVersion?.display ?: entry.availableVersion
        entry.availableVersionCode = module.repoVersion?.versionCode ?: entry.availableVersionCode
        entry.enabled = module.scope?.enabled
        entry.providerCompatibility = lsposedProviderCompatibility()
        entry.searchTokens += listOf(
            module.packageName,
            module.label,
            module.displayName,
            module.description,
        ) + module.scope?.targets.orEmpty().flatMap { listOf(it.packageName, it.label) }
        if (module.hasUpdate) {
            entry.markUpdate(
                availableVersion = module.repoVersion?.display.orEmpty(),
                availableVersionCode = module.repoVersion?.versionCode ?: Long.MIN_VALUE,
                sourceLabel = "LSPosed repository",
                compatible = inputs.lsposedProviderStatus.installed,
            )
        } else {
            entry.installState = if (module.scope?.enabled == false) UnifiedInstallState.DISABLED else UnifiedInstallState.INSTALLED
        }
        module.scope?.let { scope ->
            entry.scope = UnifiedScopeState.Lsposed(
                enabled = scope.enabled,
                autoInclude = scope.autoInclude,
                scopedPackageCount = scope.scopeCount,
                packages = scope.targets.map { it.packageName }.distinct(),
            )
            entry.scopeCount = scope.scopeCount
            entry.badges += UnifiedModuleBadge(
                kind = UnifiedBadgeKind.SCOPE,
                label = scope.scopeLabel,
                detail = scope.stateLabel,
                severity = if (scope.enabled) UnifiedBadgeSeverity.SUCCESS else UnifiedBadgeSeverity.WARNING,
            )
        } ?: run {
            entry.badges += UnifiedModuleBadge(
                kind = UnifiedBadgeKind.PROBLEM,
                label = "Scope unknown",
                detail = "Installed APK module is not present in the LSPosed provider database.",
                severity = UnifiedBadgeSeverity.WARNING,
            )
        }
        addLsposedProviderBadge(entry)
        if (!module.sourceMatched) {
            entry.badges += UnifiedModuleBadge(
                kind = UnifiedBadgeKind.PROBLEM,
                label = "Unmatched LSPosed module",
                detail = "Installed APK module was not matched to the LSPosed repository index.",
                severity = UnifiedBadgeSeverity.WARNING,
            )
        }
        entry.match = bestMatch(entry.match, UnifiedModuleMatch(UnifiedMatchReason.PACKAGE_NAME, 96, "Installed LSPosed package matched by package name.", setOf(module.packageName)))
    }

    fun addAshRescueState(ashState: AshManagerState) {
        ashState.snapshot?.modules.orEmpty().forEach { module ->
            val identity = sequenceOf(module.id, module.folder, module.name).firstOrNull { it.isNotBlank() }.orEmpty()
            val canonicalId = UnifiedModuleAliasRegistry.canonicalFor(identity)
            val entry = entry(canonicalId, identity.ifBlank { module.folder })
            entry.title = chooseTitle(entry.title, module.name, module.id.ifBlank { module.folder })
            entry.subtitle = chooseSubtitle(entry.subtitle, module.version)
            entry.sourceTypes += UnifiedModuleSourceType.RESCUE
            entry.sourceMode = mergeMode(entry.sourceMode, UnifiedModuleSourceMode.RESCUE)
            entry.enabled = entry.enabled ?: module.enabled
            entry.searchTokens += listOf(module.id, module.folder, module.name, module.trust, module.baseTrust)
            ashProtections[canonicalId]?.let { entry.applyProtection(it) }
            if (entry.installState == UnifiedInstallState.AVAILABLE || entry.installState == UnifiedInstallState.UNKNOWN) {
                entry.installState = if (module.enabled) UnifiedInstallState.INSTALLED else UnifiedInstallState.DISABLED
            }
            entry.match = bestMatch(entry.match, UnifiedModuleMatch(UnifiedMatchReason.FOLDER_NAME, 72, "AshReXcue snapshot supplied folder/id recovery evidence.", setOf(module.folder, module.id)))
        }
    }

    fun build(): List<UnifiedModuleItem> = entries.values.map { entry -> entry.toItem() }
        .sortedWith(
            compareByDescending<UnifiedModuleItem> { it.hasProblems }
                .thenByDescending { it.updateAvailable }
                .thenByDescending { it.installed }
                .thenBy { it.title.lowercase(Locale.ROOT) },
        )

    private fun entry(canonicalId: String, displayId: String): MutableUnifiedModule = entries.getOrPut(canonicalId) {
        MutableUnifiedModule(
            canonicalId = canonicalId,
            displayId = displayId,
            aliases = UnifiedModuleAliasRegistry.aliasesFor(canonicalId),
            lockedUpdateIds = inputs.lockedUpdates.map(UnifiedModuleAliasRegistry::canonicalFor).toSet(),
            updateAllowedByCanonical = inputs.updateAllowed.mapKeys { UnifiedModuleAliasRegistry.canonicalFor(it.key) },
        )
    }

    private fun maybeApplyUpdate(
        entry: MutableUnifiedModule,
        canonicalId: String,
        installedVersion: String,
        installedVersionCode: Long,
    ) {
        val update = inputs.updateCandidates[canonicalId]
            ?: return
        if (update.availableVersionCode <= installedVersionCode) return
        entry.markUpdate(
            availableVersion = update.availableVersion,
            availableVersionCode = update.availableVersionCode,
            sourceLabel = update.sourceLabel,
            compatible = update.compatible,
        )
        entry.installedVersion = installedVersion
        entry.installedVersionCode = installedVersionCode
    }

    private fun addRootProviderBadge(entry: MutableUnifiedModule) {
        entry.providerCompatibility = when {
            inputs.rootCompatibility.hasMagicMount && inputs.rootCompatibility.canRestoreModules -> UnifiedProviderCompatibility.COMPATIBLE
            inputs.rootCompatibility.hasMagicMount || inputs.rootCompatibility.canRestoreModules -> UnifiedProviderCompatibility.LIMITED
            else -> UnifiedProviderCompatibility.UNKNOWN
        }
        entry.badges += UnifiedModuleBadge(
            kind = UnifiedBadgeKind.PROVIDER_COMPATIBILITY,
            label = entry.providerCompatibility.label,
            detail = when (entry.providerCompatibility) {
                UnifiedProviderCompatibility.COMPATIBLE -> "Root manager reports Magic Mount and restore support."
                UnifiedProviderCompatibility.LIMITED -> "Root manager support is partial; some actions may need review."
                else -> "Root provider compatibility has not been fully reported yet."
            },
            severity = if (entry.providerCompatibility == UnifiedProviderCompatibility.COMPATIBLE) {
                UnifiedBadgeSeverity.SUCCESS
            } else {
                UnifiedBadgeSeverity.INFO
            },
        )
    }

    private fun addLsposedProviderBadge(entry: MutableUnifiedModule) {
        entry.badges += UnifiedModuleBadge(
            kind = UnifiedBadgeKind.PROVIDER_COMPATIBILITY,
            label = entry.providerCompatibility.label,
            detail = inputs.lsposedProviderStatus.statusLabel,
            severity = when (entry.providerCompatibility) {
                UnifiedProviderCompatibility.COMPATIBLE -> UnifiedBadgeSeverity.SUCCESS
                UnifiedProviderCompatibility.LIMITED -> UnifiedBadgeSeverity.INFO
                else -> UnifiedBadgeSeverity.WARNING
            },
        )
    }

    private fun lsposedProviderCompatibility(): UnifiedProviderCompatibility = when {
        inputs.lsposedProviderStatus.canOpen && inputs.lsposedProviderStatus.refreshBridgeAvailable -> UnifiedProviderCompatibility.COMPATIBLE
        inputs.lsposedProviderStatus.installed -> UnifiedProviderCompatibility.LIMITED
        inputs.lsposedProviderStatus.managerApkPresent -> UnifiedProviderCompatibility.LIMITED
        else -> UnifiedProviderCompatibility.UNAVAILABLE
    }

    private fun MutableUnifiedModule.applyProtection(protection: AshModuleProtection) {
        rescue = UnifiedRescueState.AshReXcue(
            folder = protection.folder,
            trust = protection.trust,
            quarantined = protection.quarantined,
            changedSinceStable = protection.changedSinceStable,
            riskBand = protection.riskBand,
            summary = protection.intelligenceSummary,
        )
        sourceTypes += UnifiedModuleSourceType.RESCUE
        searchTokens += listOf(protection.folder, protection.moduleId, protection.trust, protection.riskBand.name)
        val severity = when {
            protection.quarantined -> UnifiedBadgeSeverity.ERROR
            protection.needsReview -> UnifiedBadgeSeverity.WARNING
            else -> UnifiedBadgeSeverity.INFO
        }
        badges += UnifiedModuleBadge(
            kind = UnifiedBadgeKind.RESCUE,
            label = when {
                protection.quarantined -> "Quarantined"
                protection.needsReview -> "Needs rescue review"
                else -> "AshReXcue indexed"
            },
            detail = protection.intelligenceSummary.ifBlank { "Trust=${protection.trust}; risk=${protection.riskBand.name}" },
            severity = severity,
        )
        if (protection.quarantined) installState = UnifiedInstallState.PROBLEM
    }
}

private data class MutableUnifiedModule(
    val canonicalId: String,
    var displayId: String,
    var title: String = "",
    var subtitle: String = "",
    var description: String = "",
    var author: String? = null,
    val sourceTypes: MutableSet<UnifiedModuleSourceType> = linkedSetOf(),
    var sourceMode: UnifiedModuleSourceMode = UnifiedModuleSourceMode.UNKNOWN,
    var sourceUrl: String? = null,
    var repositoryName: String? = null,
    var artifactStrategy: GitHubArtifactStrategy? = null,
    val aliases: Set<String>,
    val lockedUpdateIds: Set<String>,
    val updateAllowedByCanonical: Map<String, Boolean>,
    var installState: UnifiedInstallState = UnifiedInstallState.UNKNOWN,
    var enabled: Boolean? = null,
    var installedVersion: String? = null,
    var installedVersionCode: Long? = null,
    var availableVersion: String? = null,
    var availableVersionCode: Long? = null,
    var providerCompatibility: UnifiedProviderCompatibility = UnifiedProviderCompatibility.NOT_APPLICABLE,
    var scope: UnifiedScopeState = UnifiedScopeState.None,
    var rescue: UnifiedRescueState = UnifiedRescueState.None,
    var match: UnifiedModuleMatch = UnifiedModuleMatch(UnifiedMatchReason.UNMATCHED, 0, "No source has matched this module yet."),
    val badges: MutableList<UnifiedModuleBadge> = mutableListOf(),
    val searchTokens: MutableSet<String> = linkedSetOf(),
    var installedAt: Long = 0L,
    var updatedAt: Long = 0L,
    var scopeCount: Int = 0,
) {
    fun markUpdate(
        availableVersion: String,
        availableVersionCode: Long,
        sourceLabel: String?,
        compatible: Boolean,
    ) {
        this.availableVersion = availableVersion.takeIf(String::isNotBlank) ?: this.availableVersion
        this.availableVersionCode = availableVersionCode.takeIf { it > Long.MIN_VALUE } ?: this.availableVersionCode
        installState = UnifiedInstallState.UPDATE_AVAILABLE
        badges += UnifiedModuleBadge(
            kind = UnifiedBadgeKind.UPDATE,
            label = "Update available",
            detail = buildString {
                append(this@MutableUnifiedModule.installedVersion ?: "Installed")
                append(" → ")
                append(this@MutableUnifiedModule.availableVersion ?: availableVersion.ifBlank { availableVersionCode.toString() })
                sourceLabel?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
            },
            severity = if (compatible) UnifiedBadgeSeverity.SUCCESS else UnifiedBadgeSeverity.WARNING,
        )
    }

    fun toItem(): UnifiedModuleItem {
        val dedupedBadges = normalizedBadges()
        val effectiveProblemSeverity = dedupedBadges.maxByOrNull { it.severity.ordinal }?.severity ?: UnifiedBadgeSeverity.INFO
        val effectiveState = UnifiedModuleState(
            installState = effectiveInstallState(),
            enabled = enabled,
            installedVersion = installedVersion,
            installedVersionCode = installedVersionCode,
            availableVersion = availableVersion,
            availableVersionCode = availableVersionCode,
            providerCompatibility = providerCompatibility,
            scope = scope,
            rescue = rescue,
        )
        val effectiveTitle = title.ifBlank { displayId.ifBlank { canonicalId } }
        val effectiveSubtitle = subtitle.ifBlank { effectiveState.installState.label }
        val tokens = (searchTokens + aliases + setOf(canonicalId, displayId, effectiveTitle, effectiveSubtitle, description, author.orEmpty(), repositoryName.orEmpty(), sourceUrl.orEmpty()))
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter(String::isNotBlank)
            .toSet()
        return UnifiedModuleItem(
            canonicalId = canonicalId,
            displayId = displayId,
            title = effectiveTitle,
            subtitle = effectiveSubtitle,
            description = description.ifBlank { "No description available." },
            author = author,
            sourceTypes = sourceTypes.toSet(),
            sourceMode = sourceMode,
            sourceUrl = sourceUrl,
            repositoryName = repositoryName,
            artifactStrategy = artifactStrategy,
            aliases = aliases,
            state = effectiveState,
            match = match,
            badges = dedupedBadges,
            searchTokens = tokens,
            sort = UnifiedModuleSortKeys(
                name = effectiveTitle.lowercase(Locale.ROOT),
                installedAt = installedAt,
                updatedAt = updatedAt,
                versionCode = maxOf(installedVersionCode ?: 0L, availableVersionCode ?: 0L),
                scopeCount = scopeCount,
                problemSeverity = effectiveProblemSeverity,
            ),
        )
    }

    private fun effectiveInstallState(): UnifiedInstallState {
        val canonical = UnifiedModuleAliasRegistry.canonicalFor(canonicalId)
        if (installState == UnifiedInstallState.UPDATE_AVAILABLE && inputsMarkerUpdateLocked(canonical)) return UnifiedInstallState.LOCKED
        if (installState == UnifiedInstallState.UPDATE_AVAILABLE && inputsMarkerUpdateIgnored(canonical)) return UnifiedInstallState.IGNORED
        return installState
    }

    private fun normalizedBadges(): List<UnifiedModuleBadge> = buildList {
        add(
            UnifiedModuleBadge(
                kind = UnifiedBadgeKind.INSTALL_STATE,
                label = effectiveInstallState().label,
                severity = when (effectiveInstallState()) {
                    UnifiedInstallState.UPDATE_AVAILABLE -> UnifiedBadgeSeverity.SUCCESS
                    UnifiedInstallState.PROBLEM -> UnifiedBadgeSeverity.ERROR
                    UnifiedInstallState.DISABLED,
                    UnifiedInstallState.REMOVE_PENDING,
                    UnifiedInstallState.UPDATE_PENDING,
                    UnifiedInstallState.IGNORED,
                    UnifiedInstallState.LOCKED,
                    -> UnifiedBadgeSeverity.WARNING
                    else -> UnifiedBadgeSeverity.INFO
                },
            ),
        )
        addAll(badges)
        artifactStrategy?.takeIf { it != GitHubArtifactStrategy.AUTO }?.let { strategy ->
            add(
                UnifiedModuleBadge(
                    kind = UnifiedBadgeKind.ARTIFACT_STRATEGY,
                    label = strategy.badgeLabel(),
                    detail = strategy.queryValue,
                    severity = UnifiedBadgeSeverity.INFO,
                ),
            )
        }
        add(
            UnifiedModuleBadge(
                kind = UnifiedBadgeKind.SOURCE_MODE,
                label = sourceMode.label,
                detail = sourceTypes.joinToString { it.label },
                severity = UnifiedBadgeSeverity.INFO,
            ),
        )
    }.distinctBy { badge -> "${badge.kind}:${badge.label}:${badge.detail}" }

    private fun inputsMarkerUpdateLocked(canonical: String): Boolean = canonical in lockedUpdateIds
    private fun inputsMarkerUpdateIgnored(canonical: String): Boolean = updateAllowedByCanonical[canonical] == false

}

private fun rootInstallState(state: State): UnifiedInstallState = when (state) {
    State.ENABLE -> UnifiedInstallState.INSTALLED
    State.DISABLE -> UnifiedInstallState.DISABLED
    State.REMOVE -> UnifiedInstallState.REMOVE_PENDING
    State.UPDATE -> UnifiedInstallState.UPDATE_PENDING
}

private fun GitHubSourceMode?.toUnifiedMode(fallback: String): UnifiedModuleSourceMode = when (this) {
    GitHubSourceMode.RELEASE -> UnifiedModuleSourceMode.RELEASE
    GitHubSourceMode.NIGHTLY -> UnifiedModuleSourceMode.NIGHTLY
    null -> when (fallback.trim().lowercase(Locale.ROOT)) {
        "release", "stable" -> UnifiedModuleSourceMode.RELEASE
        "nightly", "nightlylink", "nightly_link", "nightly-link" -> UnifiedModuleSourceMode.NIGHTLY
        "manual" -> UnifiedModuleSourceMode.MANUAL
        "local" -> UnifiedModuleSourceMode.LOCAL
        else -> UnifiedModuleSourceMode.UNKNOWN
    }
}

private fun GitHubArtifactStrategy.badgeLabel(): String = when (this) {
    GitHubArtifactStrategy.AUTO -> "Auto artifact"
    GitHubArtifactStrategy.DIRECT_MODULE_ZIP -> "Direct module ZIP"
    GitHubArtifactStrategy.NESTED_ZIP -> "Nested module ZIP"
    GitHubArtifactStrategy.EXTRACTED_MODULE_LAYOUT -> "Extracted module layout"
    GitHubArtifactStrategy.SINGLE_FOLDER_MODULE_LAYOUT -> "Single-folder layout"
}

private fun mergeMode(
    current: UnifiedModuleSourceMode,
    next: UnifiedModuleSourceMode,
): UnifiedModuleSourceMode = when {
    current == UnifiedModuleSourceMode.UNKNOWN -> next
    current == next -> current
    current == UnifiedModuleSourceMode.INSTALLED && next == UnifiedModuleSourceMode.RESCUE -> current
    current == UnifiedModuleSourceMode.RESCUE && next == UnifiedModuleSourceMode.INSTALLED -> next
    else -> UnifiedModuleSourceMode.MIXED
}

private fun chooseTitle(current: String, candidate: String, fallback: String): String = current.ifBlank {
    candidate.takeIf(String::isNotBlank) ?: fallback
}

private fun chooseSubtitle(current: String, candidate: String): String = current.ifBlank { candidate }

private fun chooseDescription(current: String, candidate: String): String = current.ifBlank { candidate }

private fun bestMatch(
    current: UnifiedModuleMatch,
    next: UnifiedModuleMatch,
): UnifiedModuleMatch = if (next.confidence > current.confidence) next else current

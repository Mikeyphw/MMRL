package com.dergoogler.mmrl.lsposed

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable
import java.util.Locale

@JsonClass(generateAdapter = true)
data class LsposedRepoModule(
    val name: String,
    val description: String? = null,
    val url: String? = null,
    val homepageUrl: String? = null,
    val latestRelease: String? = null,
    val latestReleaseTime: String? = null,
    val latestBetaRelease: String? = null,
    val latestBetaReleaseTime: String? = null,
    val latestSnapshotRelease: String? = null,
    val latestSnapshotReleaseTime: String? = null,
    val releases: List<LsposedRelease> = emptyList(),
    val betaReleases: List<LsposedRelease> = emptyList(),
    val snapshotReleases: List<LsposedRelease> = emptyList(),
    val readme: String? = null,
    val summary: String? = null,
    val scope: List<String> = emptyList(),
    val sourceUrl: String? = null,
    val hide: Boolean? = null,
    val additionalAuthors: List<String>? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null,
    val stargazerCount: Int? = null,
) {
    val packageName: String get() = name

    val displayName: String
        get() = summary?.takeIf { it.isNotBlank() }
            ?: name.substringAfterLast('.').replace('_', ' ').replace('-', ' ')

    val displayDescription: String
        get() = description?.takeIf { it.isNotBlank() }
            ?: readme?.lineSequence()?.firstOrNull { it.isNotBlank() }
            ?: "No description provided by the LSPosed repository."

    val latestStableVersion: LsposedVersion?
        get() = latestRelease?.let(LsposedVersion::parse)

    val latestStableTime: String?
        get() = latestReleaseTime ?: updatedAt

    fun withDetail(detail: LsposedRepoModule): LsposedRepoModule =
        copy(
            description = detail.description ?: description,
            url = detail.url ?: url,
            homepageUrl = detail.homepageUrl ?: homepageUrl,
            latestRelease = detail.latestRelease ?: latestRelease,
            latestReleaseTime = detail.latestReleaseTime ?: latestReleaseTime,
            latestBetaRelease = detail.latestBetaRelease ?: latestBetaRelease,
            latestBetaReleaseTime = detail.latestBetaReleaseTime ?: latestBetaReleaseTime,
            latestSnapshotRelease = detail.latestSnapshotRelease ?: latestSnapshotRelease,
            latestSnapshotReleaseTime = detail.latestSnapshotReleaseTime ?: latestSnapshotReleaseTime,
            releases = detail.releases.ifEmpty { releases },
            betaReleases = detail.betaReleases.ifEmpty { betaReleases },
            snapshotReleases = detail.snapshotReleases.ifEmpty { snapshotReleases },
            readme = detail.readme ?: readme,
            summary = detail.summary ?: summary,
            scope = detail.scope.ifEmpty { scope },
            sourceUrl = detail.sourceUrl ?: sourceUrl,
            hide = detail.hide ?: hide,
            additionalAuthors = detail.additionalAuthors ?: additionalAuthors,
            updatedAt = detail.updatedAt ?: updatedAt,
            createdAt = detail.createdAt ?: createdAt,
            stargazerCount = detail.stargazerCount ?: stargazerCount,
        )
}

@JsonClass(generateAdapter = true)
data class LsposedRelease(
    val name: String? = null,
    val url: String? = null,
    val description: String? = null,
    val descriptionHTML: String? = null,
    val createdAt: String? = null,
    val publishedAt: String? = null,
    val updatedAt: String? = null,
    val tagName: String? = null,
    val isPrerelease: Boolean? = null,
    val releaseAssets: List<LsposedReleaseAsset> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class LsposedReleaseAsset(
    val name: String? = null,
    val contentType: String? = null,
    val downloadUrl: String? = null,
    val downloadCount: Int = 0,
    val size: Int = 0,
) {
    val isApk: Boolean
        get() = name?.endsWith(".apk", ignoreCase = true) == true ||
            contentType.equals("application/vnd.android.package-archive", ignoreCase = true)
}

data class LsposedVersion(
    val versionCode: Long,
    val versionName: String,
) {
    val display: String get() = "$versionName ($versionCode)"

    companion object {
        fun parse(raw: String): LsposedVersion? {
            val parts = raw.split('-', limit = 2)
            if (parts.size != 2) return null
            val code = parts[0].toLongOrNull() ?: return null
            val name = parts[1].replace('_', ' ').trim().ifBlank { raw }
            return LsposedVersion(code, name)
        }
    }
}

data class LsposedInstalledModule(
    val packageName: String,
    val label: String,
    val installedVersionName: String?,
    val installedVersionCode: Long,
    val repoModule: LsposedRepoModule?,
    val launchable: Boolean,
    val detectedByXposedMetadata: Boolean,
) {
    val displayName: String get() = repoModule?.displayName ?: label
    val description: String get() = repoModule?.displayDescription ?: "Installed APK module. It was not matched to the LSPosed repository index."
    val repoVersion: LsposedVersion? get() = repoModule?.latestStableVersion
    val hasUpdate: Boolean get() = (repoVersion?.versionCode ?: Long.MIN_VALUE) > installedVersionCode
    val sourceMatched: Boolean get() = repoModule != null
}

object LsposedModulePolicy {
    fun bestInstallAsset(module: LsposedRepoModule): Pair<LsposedRelease, LsposedReleaseAsset>? {
        val releases = module.releases.ifEmpty { module.betaReleases }.ifEmpty { module.snapshotReleases }
        val latestTag = module.latestRelease
        val ordered =
            releases.sortedWith(
                compareByDescending<LsposedRelease> { release ->
                    if (latestTag != null && (release.name == latestTag || release.tagName == latestTag)) 1 else 0
                }.thenByDescending { it.publishedAt ?: it.updatedAt ?: it.createdAt ?: "" },
            )

        ordered.forEach { release ->
            val asset = release.releaseAssets.firstOrNull { it.isApk && !it.name.orEmpty().contains("source", ignoreCase = true) }
                ?: release.releaseAssets.firstOrNull { it.isApk }
            if (asset?.downloadUrl?.isNotBlank() == true) return release to asset
        }
        return null
    }

    fun latestVersionDisplay(module: LsposedRepoModule): String =
        module.latestStableVersion?.display ?: module.latestRelease?.replace('_', ' ') ?: "Unknown version"

    fun matchesQuery(module: LsposedRepoModule, query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isBlank()) return true
        return listOf(
            module.displayName,
            module.packageName,
            module.description.orEmpty(),
            module.summary.orEmpty(),
            module.sourceUrl.orEmpty(),
            module.homepageUrl.orEmpty(),
        ).any { it.contains(normalized, ignoreCase = true) }
    }
}


object LsposedIdentity {
    fun normalize(packageName: String): String = packageName.trim().lowercase(Locale.ROOT)
}

@Serializable
enum class LsposedVersionPolicyMode {
    FOLLOW_LATEST,
    IGNORE_UPDATES,
    PIN_CURRENT,
    MAX_VERSION_CODE,
}

@Serializable
data class LsposedVersionPolicy(
    val packageName: String,
    val mode: LsposedVersionPolicyMode = LsposedVersionPolicyMode.FOLLOW_LATEST,
    val lockedVersionName: String? = null,
    val lockedVersionCode: Long? = null,
    val maxVersionCode: Long? = null,
    val reason: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val normalizedPackageName: String
        get() = LsposedIdentity.normalize(packageName)

    val isLocked: Boolean
        get() = mode != LsposedVersionPolicyMode.FOLLOW_LATEST

    fun blocks(candidateVersionCode: Long?): Boolean = when (mode) {
        LsposedVersionPolicyMode.FOLLOW_LATEST -> false
        LsposedVersionPolicyMode.IGNORE_UPDATES -> candidateVersionCode != null
        LsposedVersionPolicyMode.PIN_CURRENT -> lockedVersionCode?.let { locked ->
            candidateVersionCode?.let { it > locked } ?: true
        } ?: true
        LsposedVersionPolicyMode.MAX_VERSION_CODE -> maxVersionCode?.let { max ->
            candidateVersionCode?.let { it > max } ?: false
        } ?: false
    }

    fun statusLabel(candidateVersionName: String? = null): String = when (mode) {
        LsposedVersionPolicyMode.FOLLOW_LATEST -> "Following latest"
        LsposedVersionPolicyMode.IGNORE_UPDATES -> "Updates ignored"
        LsposedVersionPolicyMode.PIN_CURRENT -> buildString {
            append("Locked")
            lockedVersionName?.takeIf(String::isNotBlank)?.let { append(" at ").append(it) }
            candidateVersionName?.takeIf(String::isNotBlank)?.let { append(" · newer ").append(it) }
        }
        LsposedVersionPolicyMode.MAX_VERSION_CODE -> buildString {
            append("Max")
            maxVersionCode?.let { append(" ").append(it) }
            candidateVersionName?.takeIf(String::isNotBlank)?.let { append(" · newer ").append(it) }
        }
    }

    companion object {
        fun follow(packageName: String) = LsposedVersionPolicy(
            packageName = LsposedIdentity.normalize(packageName),
            mode = LsposedVersionPolicyMode.FOLLOW_LATEST,
        )

        fun ignore(packageName: String) = LsposedVersionPolicy(
            packageName = LsposedIdentity.normalize(packageName),
            mode = LsposedVersionPolicyMode.IGNORE_UPDATES,
            reason = "Ignored from Installed LSPosed modules",
        )

        fun pinCurrent(module: LsposedInstalledModule) = LsposedVersionPolicy(
            packageName = LsposedIdentity.normalize(module.packageName),
            mode = LsposedVersionPolicyMode.PIN_CURRENT,
            lockedVersionName = module.installedVersionName,
            lockedVersionCode = module.installedVersionCode,
            maxVersionCode = module.installedVersionCode,
            reason = "Pinned current APK version",
        )

        fun maxCurrent(module: LsposedInstalledModule) = LsposedVersionPolicy(
            packageName = LsposedIdentity.normalize(module.packageName),
            mode = LsposedVersionPolicyMode.MAX_VERSION_CODE,
            lockedVersionName = module.installedVersionName,
            lockedVersionCode = module.installedVersionCode,
            maxVersionCode = module.installedVersionCode,
            reason = "Allowed only up to this APK versionCode",
        )
    }
}

@Serializable
data class LsposedSnapshot(
    val id: String,
    val label: String,
    val createdAt: Long,
    val metadataOnly: Boolean = true,
    val modules: List<LsposedSnapshotItem>,
) {
    val installedCount: Int
        get() = modules.size
}

@Serializable
data class LsposedSnapshotItem(
    val packageName: String,
    val name: String,
    val description: String,
    val installedVersionName: String? = null,
    val installedVersionCode: Long,
    val repoVersionName: String? = null,
    val repoVersionCode: Long? = null,
    val sourceMatched: Boolean,
    val launchable: Boolean,
    val detectedByXposedMetadata: Boolean,
    val policy: LsposedVersionPolicy? = null,
)

enum class LsposedSnapshotPlanStatus {
    CURRENT,
    VERSION_CHANGED,
    MISSING,
    EXTRA,
    REPO_CHANGED,
    REVIEW,
}

data class LsposedSnapshotPlanItem(
    val packageName: String,
    val title: String,
    val status: LsposedSnapshotPlanStatus,
    val summary: String,
    val destructive: Boolean = false,
)

object LsposedSnapshotPlanner {
    fun compare(
        snapshot: LsposedSnapshot,
        current: List<LsposedSnapshotItem>,
    ): List<LsposedSnapshotPlanItem> {
        val currentByPackage = current.associateBy { LsposedIdentity.normalize(it.packageName) }
        val snapshotByPackage = snapshot.modules.associateBy { LsposedIdentity.normalize(it.packageName) }
        val planned = buildList {
            snapshot.modules.forEach { saved ->
                val currentItem = currentByPackage[LsposedIdentity.normalize(saved.packageName)]
                when {
                    currentItem == null -> add(
                        LsposedSnapshotPlanItem(
                            packageName = saved.packageName,
                            title = saved.name,
                            status = LsposedSnapshotPlanStatus.MISSING,
                            summary = "APK module is missing now; install ${saved.installedVersionName ?: saved.installedVersionCode} before enabling it in LSPosed.",
                        ),
                    )
                    currentItem.installedVersionCode != saved.installedVersionCode || currentItem.installedVersionName != saved.installedVersionName -> add(
                        LsposedSnapshotPlanItem(
                            packageName = saved.packageName,
                            title = saved.name,
                            status = LsposedSnapshotPlanStatus.VERSION_CHANGED,
                            summary = "Snapshot has ${saved.installedVersionName ?: saved.installedVersionCode}; device has ${currentItem.installedVersionName ?: currentItem.installedVersionCode}.",
                        ),
                    )
                    currentItem.sourceMatched != saved.sourceMatched -> add(
                        LsposedSnapshotPlanItem(
                            packageName = saved.packageName,
                            title = saved.name,
                            status = LsposedSnapshotPlanStatus.REPO_CHANGED,
                            summary = if (saved.sourceMatched) "Snapshot matched the LSPosed repository; current install is no longer matched." else "Current install now matches the LSPosed repository.",
                        ),
                    )
                    else -> add(
                        LsposedSnapshotPlanItem(
                            packageName = saved.packageName,
                            title = saved.name,
                            status = LsposedSnapshotPlanStatus.CURRENT,
                            summary = "Matches the saved LSPosed APK snapshot.",
                        ),
                    )
                }
            }
            current.filter { currentItem -> snapshotByPackage[LsposedIdentity.normalize(currentItem.packageName)] == null }
                .forEach { extra ->
                    add(
                        LsposedSnapshotPlanItem(
                            packageName = extra.packageName,
                            title = extra.name,
                            status = LsposedSnapshotPlanStatus.EXTRA,
                            summary = "Installed after the snapshot; review before uninstalling or disabling in LSPosed.",
                            destructive = true,
                        ),
                    )
                }
        }

        return planned.sortedWith(
            compareBy<LsposedSnapshotPlanItem> { it.status == LsposedSnapshotPlanStatus.CURRENT }
                .thenByDescending { it.destructive }
                .thenBy { it.title.lowercase(Locale.ROOT) },
        )
    }
}

fun LsposedInstalledModule.toSnapshotItem(policy: LsposedVersionPolicy? = null): LsposedSnapshotItem =
    LsposedSnapshotItem(
        packageName = LsposedIdentity.normalize(packageName),
        name = displayName,
        description = description,
        installedVersionName = installedVersionName,
        installedVersionCode = installedVersionCode,
        repoVersionName = repoVersion?.versionName,
        repoVersionCode = repoVersion?.versionCode,
        sourceMatched = sourceMatched,
        launchable = launchable,
        detectedByXposedMetadata = detectedByXposedMetadata,
        policy = policy,
    )

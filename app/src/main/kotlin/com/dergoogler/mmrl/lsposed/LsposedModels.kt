package com.dergoogler.mmrl.lsposed

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

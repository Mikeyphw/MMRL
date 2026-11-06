package com.dergoogler.mmrl.network

import io.github.mikeyphw.mmrl.shared.GitHubRepository
import io.github.mikeyphw.mmrl.shared.ReleaseAssetIdentity
import io.github.mikeyphw.mmrl.shared.Sha256Digest

/**
 * First-party GitHub release metadata admission ONLY, not an APK signer or
 * release/download authorization. MMRL supports third-party module sources:
 * this policy deliberately does not intercept module downloads or installs.
 */
internal object FirstPartyReleaseMetadata {
    const val REPOSITORY = "Mikeyphw/MMRL"
    private val repository = requireNotNull(GitHubRepository.parse(REPOSITORY))

    fun matchesReleasePage(tag: String, url: String): Boolean =
        ReleaseAssetIdentity.of(repository, tag, "probe.apk")?.matchesReleasePage(url) == true

    fun matchesReleaseAsset(tag: String, name: String, url: String): Boolean =
        ReleaseAssetIdentity.of(repository, tag, name)?.matchesAssetUrl(url) == true

    fun verifiesSha256(bytes: ByteArray, digest: String): Boolean =
        Sha256Digest.parse(digest)?.verifies(bytes) == true
}

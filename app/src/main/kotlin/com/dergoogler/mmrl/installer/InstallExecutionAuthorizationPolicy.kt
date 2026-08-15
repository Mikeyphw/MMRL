package com.dergoogler.mmrl.installer

/** Authorization boundary between reviewed archives and privileged install execution. */
object InstallExecutionAuthorizationPolicy {
    private val sha256 = Regex("[0-9a-fA-F]{64}")

    fun requireAuthorizedLaunch(
        artifactCount: Int,
        requireApproval: Boolean,
        reviewedSha256: List<String>,
    ) {
        require(artifactCount > 0) { "Install requires at least one archive" }
        require(reviewedSha256.isEmpty() || reviewedSha256.size == artifactCount) {
            "Expected archive hash count must match selected archive count"
        }
        reviewedSha256.forEach { digest ->
            require(sha256.matches(digest)) { "Invalid reviewed archive SHA-256" }
        }
        if (!requireApproval) {
            require(reviewedSha256.size == artifactCount) {
                "Direct privileged installation requires one pre-reviewed SHA-256 per archive"
            }
        }
    }
}

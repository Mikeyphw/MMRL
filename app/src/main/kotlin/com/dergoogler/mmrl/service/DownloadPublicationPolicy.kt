package com.dergoogler.mmrl.service

/** Chooses a publication mechanism that never exposes a partially-written final artifact. */
object DownloadPublicationPolicy {
    private const val ANDROID_10_API = 29
    enum class PublishMode { MEDIASTORE_PENDING, ATOMIC_FILE }

    fun forDestination(sdkInt: Int, inPublicDownloads: Boolean): PublishMode =
        if (inPublicDownloads && sdkInt >= ANDROID_10_API) {
            PublishMode.MEDIASTORE_PENDING
        } else {
            PublishMode.ATOMIC_FILE
        }
}

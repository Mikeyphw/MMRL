package com.dergoogler.mmrl.ui.activity.terminal.install

internal object ExternalInstallRequestPolicy {
    private const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val CONTENT_SCHEME = "content"
    private const val ZIP_MIME_TYPE = "application/zip"

    fun accepts(
        action: String?,
        scheme: String?,
        mimeType: String?,
    ): Boolean =
        action == ACTION_VIEW &&
            scheme.equals(CONTENT_SCHEME, ignoreCase = true) &&
            mimeType.equals(ZIP_MIME_TYPE, ignoreCase = true)
}

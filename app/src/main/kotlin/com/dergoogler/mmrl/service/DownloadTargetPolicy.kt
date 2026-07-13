package com.dergoogler.mmrl.service

import java.io.File

enum class ExistingDownload {
    MISSING,
    EMPTY,
    VALID,
}

object DownloadTargetPolicy {
    fun classify(file: File): ExistingDownload =
        when {
            !file.exists() -> ExistingDownload.MISSING
            file.length() <= 0L -> ExistingDownload.EMPTY
            else -> ExistingDownload.VALID
        }
}

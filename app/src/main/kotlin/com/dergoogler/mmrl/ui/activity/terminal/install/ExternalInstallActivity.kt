package com.dergoogler.mmrl.ui.activity.terminal.install

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Narrow exported ingress. Caller extras are intentionally never forwarded to privileged install state. */
class ExternalInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        if (uri == null || !ExternalInstallRequestPolicy.accepts(intent.action, uri.scheme, intent.type)) {
            finish()
            return
        }

        InstallActivity.startExternalReviewed(
            context = this,
            uri = uri,
            grantFlags = intent.flags and URI_GRANT_FLAGS,
        )
        finish()
    }

    companion object {
        private const val URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
}

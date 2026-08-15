package com.dergoogler.mmrl.platform.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/** Content URIs remain capability tokens and are never rewritten into root-openable paths. */
class SuContentResolver internal constructor(context: Context) : ContentResolver(context) {
    fun openSuInputStream(uri: Uri?): InputStream? {
        if (uri == null) return null
        return when (uri.scheme?.lowercase()) {
            ContentResolver.SCHEME_FILE -> uri.path?.let(::SuFileInputStream)
            ContentResolver.SCHEME_CONTENT -> super.openInputStream(uri)
            else -> super.openInputStream(uri)
        }
    }

    fun openSuOutputStream(uri: Uri?): OutputStream? {
        if (uri == null) return null
        return when (uri.scheme?.lowercase()) {
            ContentResolver.SCHEME_FILE -> uri.path?.let(::SuFileOutputStream)
            ContentResolver.SCHEME_CONTENT -> super.openOutputStream(uri)
            else -> super.openOutputStream(uri)
        }
    }
}

val Context.suContentResolver: SuContentResolver get() = SuContentResolver(this)

package com.dergoogler.mmrl.compat

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.system.Os
import androidx.annotation.RequiresApi
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException

object MediaStoreCompat {
    private fun Context.getDisplayNameForUri(uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.toFile().name
        }

        require(uri.scheme == "content") { "Uri lacks 'content' scheme: $uri" }

        val displayName =
            runCatching {
                val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
            }.getOrNull()

        return displayName
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "module.zip"
    }

    private fun createDownloadUri(path: String) =
        Environment
            .getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS,
            ).let {
                val file = File(it, path)
                file.parentFile?.apply { if (!exists()) mkdirs() }
                file.toUri()
            }

    fun Context.createDownloadUri(
        path: String,
        mimeType: String,
    ) = when {
        BuildCompat.atLeastR ->
            runCatching {
                createMediaStoreUri(
                    file = File(Environment.DIRECTORY_DOWNLOADS, path),
                    mimeType = mimeType,
                )
            }.getOrElse {
                createDownloadUri(path)
            }

        else -> createDownloadUri(path)
    }

    /**
     * Returns a filesystem path only when Android exposes one for the supplied URI. Callers that
     * need a stable path across process or mount namespaces must use [materializeFileForUri].
     */
    fun Context.getPathForUri(uri: Uri): String? {
        val safeUri = findFileForUri(uri) ?: return null

        if (safeUri.scheme == "file") {
            return safeUri.toFile().path
        }

        if (safeUri.scheme != "content") return null

        val real =
            if (DocumentsContract.isTreeUri(safeUri)) {
                DocumentFile.fromTreeUri(this, safeUri)?.uri ?: safeUri
            } else {
                safeUri
            }

        return runCatching {
            contentResolver.openFileDescriptor(real, "r")?.use {
                Os.readlink("/proc/self/fd/${it.fd}")
            }
        }.getOrNull()
    }

    private fun Context.findFileForUri(uri: Uri): Uri? =
        when (uri.scheme) {
            "file" -> {
                val file = uri.path?.let(::File) ?: return null
                uri.takeIf { file.isFile }
            }

            "content" ->
                runCatching {
                    contentResolver.openFileDescriptor(uri, "r")?.use { uri }
                }.getOrNull()

            else -> null
        }

    fun Context.copyToDir(
        uri: Uri,
        dir: File,
    ): File? {
        if (uri.scheme != "content" && uri.scheme != "file") return null
        if (!dir.exists() && !dir.mkdirs()) return null

        val displayName = File(getDisplayNameForUri(uri)).name
        val targetName = displayName.takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "module.zip"
        val target = uniqueTarget(dir, targetName)
        val partial = dir.resolve(".${target.name}.${System.nanoTime()}.part")

        return runCatching {
            val copiedBytes =
                contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                    partial.outputStream().buffered().use { output -> input.copyTo(output) }
                } ?: throw IOException("Cannot open source URI: $uri")
            if (copiedBytes <= 0L || !partial.isFile || partial.length() != copiedBytes) {
                throw IOException("Source URI produced an empty or incomplete file: $uri")
            }

            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = false)
                partial.delete()
            }
            target.takeIf { it.isFile && it.length() > 0L }
        }.getOrElse {
            partial.delete()
            target.delete()
            null
        }
    }

    private fun uniqueTarget(
        dir: File,
        filename: String,
    ): File {
        val initial = dir.resolve(filename)
        if (!initial.exists()) return initial

        val dot = filename.lastIndexOf('.')
        val stem = if (dot > 0) filename.substring(0, dot) else filename
        val extension = if (dot > 0) filename.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = dir.resolve("$stem ($index)$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    /**
     * Resolves a URI to a readable file, copying provider-backed content into [dir] when direct
     * filesystem access is unavailable. This is the safe bridge for archive inspection and root
     * installers that require a path.
     */
    fun Context.materializeFileForUri(
        uri: Uri,
        dir: File,
    ): File? =
        when (uri.scheme) {
            "file" ->
                uri.path
                    ?.let(::File)
                    ?.takeIf { it.isFile && it.canRead() }

            // A /proc/self/fd readlink from a content URI may point at a transient mount alias such
            // as /mnt/user/0/emulated/0. That path can disappear or be invisible to the root
            // installer even though the provider can still read the file. Always copy provider
            // content to an app-owned stable file before inspection or installation.
            "content" -> copyToDir(uri, dir)
            else -> null
        }

    @RequiresApi(Build.VERSION_CODES.R)
    fun Context.createMediaStoreUri(
        file: File,
        collection: Uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL),
        mimeType: String,
    ): Uri {
        val entry =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.RELATIVE_PATH, file.parent)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            }

        return contentResolver.insert(collection, entry) ?: throw IOException("Cannot insert $file")
    }
}

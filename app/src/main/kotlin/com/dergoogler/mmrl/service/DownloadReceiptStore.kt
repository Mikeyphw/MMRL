package com.dergoogler.mmrl.service

import android.content.Context
import android.net.Uri
import com.dergoogler.mmrl.installer.ArtifactDigest
import com.dergoogler.mmrl.installer.ArtifactProvenance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Provenance receipt required before an existing download can be reused. */
@Singleton
class DownloadReceiptStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("download_receipts_v1", Context.MODE_PRIVATE)

    fun record(uri: Uri, sourceUrl: String, sha256: String, size: Long, destinationPath: String? = null) {
        require(size > 0L)
        val json = JSONObject()
            .put("uri", uri.toString())
            .put("sourceUrl", sourceUrl)
            .put("sha256", sha256)
            .put("size", size)
            .put("capturedAt", System.currentTimeMillis())
            .apply { destinationPath?.let { put("destinationPath", it) } }
        prefs.edit().apply {
            putString(key(uri), json.toString())
            destinationPath?.let { putString(destinationKey(it), uri.toString()) }
        }.apply()
    }

    fun load(uri: Uri): ArtifactProvenance? = runCatching {
        val json = JSONObject(prefs.getString(key(uri), null) ?: return null)
        if (json.getString("uri") != uri.toString()) return null
        ArtifactProvenance(
            sourceUri = uri.toString(),
            sourceUrl = json.getString("sourceUrl"),
            sha256 = json.getString("sha256"),
            size = json.getLong("size"),
            capturedAt = json.getLong("capturedAt"),
        )
    }.getOrNull()

    suspend fun verify(uri: Uri, expectedSourceUrl: String? = null): ArtifactProvenance? = withContext(Dispatchers.IO) {
        val receipt = load(uri) ?: return@withContext null
        val digest = runCatching {
            context.contentResolver.openInputStream(uri)?.buffered()?.use(ArtifactDigest::of)
                ?: return@runCatching null
        }.getOrNull() ?: return@withContext null
        receipt.takeIf { DownloadReusePolicy.matches(it, digest, expectedSourceUrl) }
    }

    suspend fun verifyDestination(destinationPath: String, expectedSourceUrl: String): ArtifactProvenance? {
        val uriValue = prefs.getString(destinationKey(destinationPath), null) ?: return null
        return verify(Uri.parse(uriValue), expectedSourceUrl)
    }

    fun forget(uri: Uri) {
        prefs.edit().remove(key(uri)).apply()
    }

    private fun destinationKey(path: String): String = "destination:" + sha(path)

    private fun key(uri: Uri): String = "uri:" + sha(uri.toString())

    private fun sha(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

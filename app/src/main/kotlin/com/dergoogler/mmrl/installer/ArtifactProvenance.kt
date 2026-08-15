package com.dergoogler.mmrl.installer

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

data class ArtifactProvenance(
    val sourceUri: String,
    val sourceUrl: String? = null,
    val sha256: String,
    val size: Long,
    val capturedAt: Long = System.currentTimeMillis(),
)

object ArtifactDigest {
    fun of(file: File): Digest = file.inputStream().buffered().use(::of)

    fun of(input: InputStream): Digest {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            size = Math.addExact(size, count.toLong())
            digest.update(buffer, 0, count)
        }
        return Digest(digest.digest().joinToString("") { "%02x".format(it) }, size)
    }

    data class Digest(val sha256: String, val size: Long)
}

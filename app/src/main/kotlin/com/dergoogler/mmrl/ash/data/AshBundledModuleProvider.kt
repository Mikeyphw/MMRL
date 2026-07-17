package com.dergoogler.mmrl.ash.data

import android.content.Context
import com.dergoogler.mmrl.ash.model.AshBundledModuleMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AshBundledModuleProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @Volatile
    private var cached: AshBundledModuleMetadata? = null

    fun metadata(): AshBundledModuleMetadata = cached ?: synchronized(this) {
        cached ?: readMetadata().also { cached = it }
    }

    private fun readMetadata(): AshBundledModuleMetadata {
        val properties = context.assets.open(ASH_MODULE_ZIP_ASSET).use { input ->
            ZipInputStream(input).use { zip ->
                generateSequence(zip::getNextEntry)
                    .firstOrNull { entry -> entry.name.removePrefix("./") == MODULE_PROP }
                    ?.let {
                        zip.bufferedReader().useLines { lines ->
                            lines.map(String::trim)
                                .filter { line -> line.isNotEmpty() && !line.startsWith('#') && '=' in line }
                                .associate { line ->
                                    line.substringBefore('=').trim() to line.substringAfter('=').trim()
                                }
                        }
                    }
            }
        } ?: throw IllegalStateException("Bundled AshReXcue module has no module.prop")

        return AshBundledModuleMetadata(
            id = properties["id"].orEmpty().ifBlank { "AshLooper" },
            name = properties["name"].orEmpty().ifBlank { "AshReXcue BootLoop Protector" },
            version = properties["version"].orEmpty(),
            versionCode = properties["versionCode"]?.toIntOrNull() ?: 0,
        )
    }

    companion object {
        const val ASH_MODULE_ZIP_ASSET = "AshReXcue_Bootloop_Protector.zip"
        private const val MODULE_PROP = "module.prop"
    }
}

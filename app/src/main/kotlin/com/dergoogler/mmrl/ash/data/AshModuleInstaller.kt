package com.dergoogler.mmrl.ash.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.dergoogler.mmrl.ash.model.AshBundledModuleMetadata
import com.dergoogler.mmrl.ash.model.AshInstallMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AshModuleInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bundledModuleProvider: AshBundledModuleProvider,
) {
    data class PreparedInstall(
        val uri: Uri,
        val mode: AshInstallMode,
        val metadata: AshBundledModuleMetadata,
    )

    suspend fun prepare(mode: AshInstallMode): PreparedInstall = withContext(Dispatchers.IO) {
        val metadata = bundledModuleProvider.metadata()
        val directory = File(context.cacheDir, "ashrexcue").apply { mkdirs() }
        directory.listFiles { file -> file.extension.equals("zip", ignoreCase = true) }
            .orEmpty()
            .forEach(File::delete)
        val module = File(
            directory,
            "AshReXcue-${metadata.versionCode}-${mode.name.lowercase()}.zip",
        )
        context.assets.open(AshBundledModuleProvider.ASH_MODULE_ZIP_ASSET).use { input ->
            module.outputStream().use(input::copyTo)
        }
        require(module.length() > 0L) { "Bundled AshReXcue module ZIP is empty" }
        PreparedInstall(
            uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                module,
            ),
            mode = mode,
            metadata = metadata,
        )
    }
}

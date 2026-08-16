package com.dergoogler.mmrl.release

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinalFileProviderContentUriInstrumentedTest {
    @Test
    fun exportedLogAndSupportBundleUrisUseAppScopedContentAuthority() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "release-seal/share/test.txt")
        file.parentFile?.mkdirs()
        file.writeText("release-seal")

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.provider", uri.authority)
        assertTrue(uri.path.orEmpty().contains("test.txt"))
    }
}

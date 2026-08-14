package com.dergoogler.mmrl.ui.activity.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PrivilegedIngressManifestTest {
    private val root = repositoryRoot()
    private val manifest =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(root.resolve("app/src/main/AndroidManifest.xml"))

    @Test
    fun privilegedActivitiesAreInternalAndOnlyNarrowInstallIngressIsExported() {
        val install = activityEndingWith(".ui.activity.terminal.install.InstallActivity")
        val action = activityEndingWith(".ui.activity.terminal.action.ActionActivity")
        val ingress = activityEndingWith(".ui.activity.terminal.install.ExternalInstallActivity")

        assertEquals("false", install.getAttributeNS(ANDROID_NS, "exported"))
        assertEquals(0, install.getElementsByTagName("intent-filter").length)
        assertEquals("false", action.getAttributeNS(ANDROID_NS, "exported"))
        assertEquals(0, action.getElementsByTagName("intent-filter").length)

        assertEquals("true", ingress.getAttributeNS(ANDROID_NS, "exported"))
        val filters = ingress.getElementsByTagName("intent-filter")
        assertEquals(1, filters.length)
        val data = (filters.item(0) as Element).getElementsByTagName("data")
        assertTrue((0 until data.length).map { data.item(it) as Element }.any {
            it.getAttributeNS(ANDROID_NS, "scheme") == "content" &&
                it.getAttributeNS(ANDROID_NS, "mimeType") == "application/zip"
        })
    }

    private fun activityEndingWith(suffix: String): Element {
        val nodes = manifest.getElementsByTagName("activity")
        val match = (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .firstOrNull { it.getAttributeNS(ANDROID_NS, "name").endsWith(suffix) }
        assertNotNull("Missing activity $suffix", match)
        return match!!
    }

    private fun repositoryRoot(): File =
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { file ->
            file.parentFile
        }.first { candidate ->
            candidate.resolve("settings.gradle.kts").isFile &&
                candidate.resolve("app/src/main/AndroidManifest.xml").isFile
        }

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}

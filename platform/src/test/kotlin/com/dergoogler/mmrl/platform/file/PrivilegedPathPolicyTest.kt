package com.dergoogler.mmrl.platform.file

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedPathPolicyTest {
    @Test
    fun `path must stay beneath approved root`() {
        val root = Files.createTempDirectory("mmrl-path-root")
        val child = root.resolve("module/file")
        assertEquals(child.normalize(), PrivilegedPathPolicy.resolveForRoots(child.toString(), listOf(root)))
        assertThrows(SecurityException::class.java) {
            PrivilegedPathPolicy.resolveForRoots(root.resolve("../outside").toString(), listOf(root))
        }
    }

    @Test
    fun `symlink component cannot escape root`() {
        val root = Files.createTempDirectory("mmrl-path-root")
        val outside = Files.createTempDirectory("mmrl-path-outside")
        val link = root.resolve("link")
        Files.createSymbolicLink(link, outside)
        assertThrows(SecurityException::class.java) {
            PrivilegedPathPolicy.resolveForRoots(link.resolve("victim").toString(), listOf(root))
        }
    }

    @Test
    fun `recursive delete removes symlink but never its target`() {
        val root = Files.createTempDirectory("mmrl-delete-root")
        val outside = Files.createTempDirectory("mmrl-delete-outside")
        val outsideFile = Files.writeString(outside.resolve("keep"), "safe")
        val tree = Files.createDirectories(root.resolve("tree/child"))
        Files.writeString(tree.resolve("inside"), "delete")
        Files.createSymbolicLink(root.resolve("tree/link"), outside)

        assertTrue(PrivilegedPathPolicy.deleteTreeNoFollow(root.resolve("tree")))
        assertFalse(Files.exists(root.resolve("tree")))
        assertTrue(Files.exists(outsideFile))
    }

    @Test
    fun finalSymlinkCanBeInspectedButCannotBeTraversed() {
        val root = Files.createTempDirectory("mmrl-path-policy")
        val target = Files.createTempFile(root, "target", ".txt")
        val link = root.resolve("link")
        Files.createSymbolicLink(link, target.fileName)

        assertThrows(SecurityException::class.java) {
            PrivilegedPathPolicy.resolveForRoots(link.toString(), listOf(root))
        }
        assertEquals(
            link,
            PrivilegedPathPolicy.resolveForRoots(link.toString(), listOf(root), allowFinalSymlink = true),
        )
    }

    @Test
    fun `safe-open resolution exposes approved root and relative path`() {
        val root = Files.createTempDirectory("mmrl-open-root")
        val child = root.resolve("a/b/file")
        val resolution = PrivilegedPathPolicy.resolveForRootsWithRoot(child.toString(), listOf(root))
        assertEquals(root.normalize(), resolution.root)
        assertEquals(Paths.get("a/b/file"), resolution.relative)
    }

    @Test
    fun descriptorPseudoPathsAreRejectedAsFilesystemCapabilities() {
        assertThrows(IllegalArgumentException::class.java) {
            PrivilegedPathPolicy.resolveForRoots("/proc/self/fd/7", listOf(Paths.get("/proc")))
        }
    }
}

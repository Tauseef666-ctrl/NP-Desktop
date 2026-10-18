/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundledVlcTest {

    private fun tempDir(): File = Files.createTempDirectory("bundled-vlc-test").toFile()

    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val file = Files.createTempFile("vlc-test", ".zip").toFile()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `extracts only playback-relevant files and marks the directory ready`() {
        val pluginBytes = byteArrayOf(1, 2, 3)
        val zip = zipOf(
            "vlc-3.0.21/libvlc.dll" to byteArrayOf(9),
            "vlc-3.0.21/libvlccore.dll" to byteArrayOf(8),
            "vlc-3.0.21/COPYING.txt" to "license".toByteArray(),
            "vlc-3.0.21/plugins/a52dec/liba52_plugin.dll" to pluginBytes,
            "vlc-3.0.21/lua/playlist/xspf.lua" to "lua".toByteArray(),
            "vlc-3.0.21/vlc.exe" to byteArrayOf(7),
            "vlc-3.0.21/locale/en.mo" to byteArrayOf(6)
        )
        val target = File(tempDir(), "extract")
        val libDir = BundledVlc.extract(zip.toURI().toURL(), target)

        assertNotNull(libDir, "libvlc.dll directory must be found")
        assertEquals("libvlc.dll", File(libDir, "libvlc.dll").name)
        assertTrue(File(libDir, "libvlc.dll").isFile)
        assertTrue(File(libDir, "libvlccore.dll").isFile)
        assertTrue(File(libDir, "COPYING.txt").isFile)
        val plugin = File(target, "vlc-3.0.21/plugins/a52dec/liba52_plugin.dll")
        assertTrue(plugin.isFile, "plugin tree must be extracted")
        assertContentEquals(pluginBytes, plugin.readBytes())
        assertTrue(File(target, "vlc-3.0.21/lua/playlist/xspf.lua").isFile)

        assertTrue(!File(target, "vlc-3.0.21/vlc.exe").exists(), "vlc.exe must not be extracted")
        assertTrue(!File(target, "vlc-3.0.21/locale/en.mo").exists(), "locales must not be extracted")
        assertTrue(File(target, ".ready").isFile, "ready marker must be written")
    }

    @Test
    fun `rejects zip-slip entries`() {
        val zip = zipOf(
            "vlc-3.0.21/libvlc.dll" to byteArrayOf(1),
            "vlc-3.0.21/plugins/../../../evil.dll" to byteArrayOf(2)
        )
        val target = File(tempDir(), "extract")
        assertFailsWith<IOException> { BundledVlc.extract(zip.toURI().toURL(), target) }
    }

    @Test
    fun `returns null when the zip has no usable libraries`() {
        val zip = zipOf("vlc-3.0.21/vlc.exe" to byteArrayOf(1))
        val target = File(tempDir(), "extract")
        assertNull(BundledVlc.extract(zip.toURI().toURL(), target))
    }

    @Test
    fun `no bundle resource in shared tests means ensureExtracted returns null`() {
        // :shared tests never carry the desktop jar's vlc-win64.zip resource.
        assertNull(BundledVlc.ensureExtracted())
    }

    @Test
    fun `discovery strategy requires libvlc in the directory`() {
        val empty = tempDir()
        val strategy = BundledVlcDiscoveryStrategy(empty)
        assertTrue(!strategy.supported())
        assertNull(strategy.discover())

        File(empty, "libvlc.dll").writeBytes(byteArrayOf(1))
        assertTrue(strategy.supported())
        assertEquals(empty.absolutePath, strategy.discover())
    }

    @Test
    fun `extract is deterministic for identical input`() {
        val entries = arrayOf(
            "vlc-3.0.21/libvlc.dll" to byteArrayOf(1, 2),
            "vlc-3.0.21/plugins/x/plugin.dll" to byteArrayOf(3)
        )
        val zip1 = zipOf(*entries)
        val zip2 = zipOf(*entries)
        val a = File(tempDir(), "a")
        val b = File(tempDir(), "b")
        val libA = BundledVlc.extract(zip1.toURI().toURL(), a)
        val libB = BundledVlc.extract(zip2.toURI().toURL(), b)
        assertNotNull(libA)
        assertNotNull(libB)
        assertTrue(
            File(a, "vlc-3.0.21/libvlc.dll").readBytes()
                .contentEquals(File(b, "vlc-3.0.21/libvlc.dll").readBytes())
        )
    }

    @Test
    fun `unused URL parameter type stays compatible`() {
        // Sanity: extract accepts a URL; a bogus protocol must fail cleanly (null via ensureExtracted
        // handles Throwable, but extract itself surfaces it — used only through ensureExtracted).
        val bogus: URL = URL("file:///nonexistent-vlc-test.zip")
        // Missing file -> IOException from openStream; caller (ensureExtracted) converts to null.
        assertFailsWith<IOException> { BundledVlc.extract(bogus, tempDir()) }
    }
}

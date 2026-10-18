/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import java.io.File
import java.io.IOException
import java.net.URL
import java.util.Properties
import java.util.zip.ZipInputStream
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy

/**
 * Extracts the VLC runtime bundled inside the desktop jar (`vlc-win64.zip`, downloaded by the
 * desktopApp `downloadVlcBundle` Gradle task) into a per-user directory, so libVLC can load its
 * native libraries from disk — DLLs cannot be loaded from inside a jar.
 *
 * Everything here is best-effort. When the bundle resource is absent (shared tests, non-Windows
 * builds) or extraction fails, [ensureExtracted] returns `null` and the engine falls back to
 * normal system-VLC discovery; nothing ever crashes dependency injection or playback setup.
 *
 * Only the files playback needs are extracted: the two core DLLs, the plugin tree, lua scripts
 * and the license texts. The official zip's `vlc.exe`, skins and locales are skipped.
 */
internal object BundledVlc {

    private const val ZIP_RESOURCE = "vlc-win64.zip"
    private const val PROPS_RESOURCE = "vlc-bundle.properties"
    private const val READY_MARKER = ".ready"

    /**
     * The directory containing `libvlc.dll` of the extracted bundle, or `null` when no bundle is
     * present in this build or extraction failed. Extraction happens once per version; later
     * calls return the existing directory after a cheap marker check.
     */
    fun ensureExtracted(): File? {
        val version = bundleVersion() ?: return null
        val zipUrl = BundledVlc::class.java.classLoader.getResource(ZIP_RESOURCE) ?: return null
        val root = targetRoot() ?: return null
        val versionDir = File(root, "vlc-$version")
        if (File(versionDir, READY_MARKER).isFile) {
            return findLibDir(versionDir)
        }
        return try {
            extract(zipUrl, versionDir)
        } catch (t: Throwable) {
            System.err.println("[NewPipe+] bundled libVLC extraction failed: $t")
            versionDir.deleteRecursively()
            null
        }
    }

    /** Delete a previously extracted bundle (used when the bundled libraries fail to load). */
    fun discard(extractedDir: File) {
        extractedDir.parentFile?.deleteRecursively()
    }

    /**
     * Extract the wanted subset of the VLC zip into [dir]. Visible for tests; production goes
     * through [ensureExtracted]. Returns the directory containing `libvlc.dll`, or `null` when
     * the zip contained no usable libraries.
     */
    internal fun extract(zipUrl: URL, dir: File): File? {
        dir.deleteRecursively()
        dir.mkdirs()
        var extracted = 0
        zipUrl.openStream().buffered().use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.replace('\\', '/').removePrefix("/")
                    if (!isWanted(name)) continue
                    val dest = File(dir, name)
                    // Zip-slip guard: the resolved path must stay inside the target directory.
                    if (!dest.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
                        throw IOException("zip slip rejected: ${entry.name}")
                    }
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { zip.copyTo(it) }
                    extracted++
                }
            }
        }
        if (extracted == 0) return null
        val libDir = findLibDir(dir) ?: return null
        File(dir, READY_MARKER).writeText("ok\n")
        return libDir
    }

    private fun isWanted(name: String): Boolean {
        val file = name.substringAfterLast('/')
        if (file == "libvlc.dll" || file == "libvlccore.dll" || file.startsWith("COPYING")) {
            return true
        }
        return name.contains("/plugins/") || name.contains("/lua/")
    }

    private fun findLibDir(dir: File): File? =
        dir.walkTopDown()
            .maxDepth(3)
            .firstOrNull { it.isFile && it.name == "libvlc.dll" }
            ?.parentFile

    private fun bundleVersion(): String? {
        val props = Properties()
        val stream = BundledVlc::class.java.classLoader.getResourceAsStream(PROPS_RESOURCE)
            ?: return null
        stream.use { props.load(it) }
        return props.getProperty("vlc.version")?.takeIf { it.isNotBlank() }
    }

    private fun targetRoot(): File? {
        val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home") ?: return null
        return File(base, "YT Desktop")
    }
}

/**
 * vlcj discovery strategy for an extracted bundled VLC directory. Tried before the platform
 * default strategies, so a bundled runtime always wins over any system-installed VLC.
 */
internal class BundledVlcDiscoveryStrategy(private val dir: File) : NativeDiscoveryStrategy {

    override fun supported(): Boolean = File(dir, "libvlc.dll").isFile

    override fun discover(): String? = if (supported()) dir.absolutePath else null

    override fun onFound(path: String): Boolean = true

    override fun onSetPluginPath(path: String): Boolean =
        try {
            val plugins = File(path, "plugins").absolutePath
            LibC.INSTANCE._putenv("VLC_PLUGIN_PATH=$plugins") == 0
        } catch (t: Throwable) {
            false
        }
}

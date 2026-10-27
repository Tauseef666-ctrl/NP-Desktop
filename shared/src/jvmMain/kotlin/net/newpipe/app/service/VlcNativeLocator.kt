/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Finds the libVLC installation and makes the native bindings load it from there.
 *
 * The lookup is done once per process. The following locations are checked, in this order:
 *
 * 1. the `newpipe.vlc.path` system property,
 * 2. the `NEWPIPE_VLC_PATH` environment variable,
 * 3. the copy extracted from the `vlc-runtime.zip` shipped inside the application jar to
 *    `%LOCALAPPDATA%/NP-Desktop/vlc`,
 * 4. the libVLC copy bundled as plain resources with the packaged application (`resources/vlc`),
 * 5. the working directory and its `vlc` subdirectory, which is where the packaged application
 *    keeps its copy of libVLC,
 * 6. the directory the Gradle `vlc-setup` plugin extracts libVLC to, which is used while
 *    developing,
 * 7. the default installation directories of VLC for Windows.
 *
 * When no installation is found, the native bindings are left to their own discovery, which is
 * able to find libVLC in the `PATH` of the system.
 */
object VlcNativeLocator {

    /**
     * The system property that overrides the directory libVLC is loaded from.
     */
    const val PROPERTY = "newpipe.vlc.path"

    /**
     * The environment variable that overrides the directory libVLC is loaded from.
     */
    const val ENVIRONMENT_VARIABLE = "NEWPIPE_VLC_PATH"

    /**
     * The system property that points at the resources directory of a packaged application.
     */
    private const val RESOURCES_PROPERTY = "compose.application.resources.dir"

    /**
     * The name of the native library, which is the marker of a libVLC installation.
     */
    private const val LIBRARY_NAME = "libvlc.dll"

    /**
     * The resource inside the application jar that contains the bundled libVLC. The name has
     * no leading slash, because [ClassLoader.getResourceAsStream] resolves it literally.
     */
    private const val RUNTIME_ZIP = "vlc-runtime.zip"

    /**
     * The zip archive is extracted here on the first run of a packaged application.
     */
    private const val EXTRACTION_MARKER = "bundle-version.txt"

    @Volatile
    private var locatedDirectory: File? = null

    private var searched = false

    /**
     * Looks for libVLC and configures the native bindings to load it from there.
     *
     * @return the directory libVLC was found in, or `null` when it could not be found
     */
    fun locate(): File? {
        locatedDirectory?.let { return it }

        synchronized(this) {
            if (searched) return locatedDirectory
            searched = true

            extractRuntime()
            val directory = candidateDirectories().firstOrNull { File(it, LIBRARY_NAME).isFile }
            if (directory == null) {
                Logger.w(messageString = "No libVLC installation was found")
            } else {
                System.setProperty("jna.library.path", directory.absolutePath)
                locatedDirectory = directory
                Logger.i(messageString = "Using libVLC from ${directory.absolutePath}")
            }

            return locatedDirectory
        }
    }

    /**
     * Unpacks the libVLC runtime that ships inside the application jar to the user's local
     * application data directory, unless the current extraction is already up to date.
     */
    private fun extractRuntime() {
        // Probe the resource and read the version marker through a separate stream, so that
        // the unzip pass below receives an untouched stream.
        val probe = classLoader.getResourceAsStream(RUNTIME_ZIP) ?: run {
            println("VlcRuntime: no bundled vlc-runtime.zip on the classpath")
            return
        }
        val version = runCatching { probe.read().toString() }.getOrNull() ?: return
        val zip = classLoader.getResourceAsStream(RUNTIME_ZIP) ?: return

        val targetDirectory = extractionDirectory()
        if (targetDirectory == null) {
            Logger.w(messageString = "No writable directory to extract the bundled libVLC to")
            return
        }

        val marker = File(targetDirectory, EXTRACTION_MARKER)
        if (marker.isFile && marker.readText() == version && File(targetDirectory, LIBRARY_NAME).isFile) {
            return
        }

        // Wipe out an existing extraction first, so that stale files cannot shadow the
        // natives of a new version.
        targetDirectory.deleteRecursively()
        targetDirectory.mkdirs()

        runCatching { unzip(zip, targetDirectory) }
            .onSuccess {
                marker.writeText(version)
                Logger.i(messageString = "Extracted the bundled libVLC to ${targetDirectory.absolutePath}")
            }
            .onFailure {
                it.printStackTrace()
                println("Failed to extract the bundled libVLC to ${targetDirectory.absolutePath}: $it")
            }
    }

    /**
     * The class loader the application was started through. `getResourceAsStream` resolves
     * through the same hierarchy in development runs and packaged applications.
     */
    private val classLoader: ClassLoader
        get() = VlcNativeLocator::class.java.classLoader ?: ClassLoader.getSystemClassLoader()

    /**
     * The directory into which the bundled runtime is extracted, or `null` when there is none.
     */
    private fun extractionDirectory(): File? {
        val localAppData = System.getenv("LOCALAPPDATA")
        val userHome = System.getProperty("user.home")
        val base = when {
            !localAppData.isNullOrBlank() -> File(localAppData, "NP-Desktop")
            !userHome.isNullOrBlank() -> File(userHome, "AppData/Local/NP-Desktop")
            else -> null
        } ?: return null

        return File(base, "vlc")
    }

    /**
     * Copies the zip archive stream into the given directory.
     */
    private fun unzip(input: InputStream, destination: File) {
        input.buffered().use { buffered ->
            ZipInputStream(buffered).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break

                    if (!entry.isDirectory) {
                        val entryFile = File(destination, entry.name)
                        // Normalise the path, so that entries cannot escape the
                        // extraction directory.
                        if (!entryFile.canonicalPath.startsWith(destination.canonicalPath + File.separator) &&
                            !entryFile.canonicalPath.equals(destination.canonicalPath, ignoreCase = true)
                        ) {
                            zip.closeEntry()
                            continue
                        }
                        val parent = entryFile.parentFile
                        parent?.mkdirs()
                        FileOutputStream(entryFile).use { output -> zip.copyTo(output) }
                        entryFile.setLastModified(entry.time)
                    }

                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * All directories that may contain a libVLC installation, in the order they are checked.
     */
    private fun candidateDirectories(): List<File> {
        val candidates = mutableListOf<File>()

        System.getProperty(PROPERTY)
            ?.takeIf { it.isNotBlank() }
            ?.let { candidates.add(File(it)) }

        System.getenv(ENVIRONMENT_VARIABLE)
            ?.takeIf { it.isNotBlank() }
            ?.let { candidates.add(File(it)) }

        // The libVLC copy that was extracted from the bundled runtime zip.
        extractionDirectory()?.let { candidates.add(it) }

        // The libVLC copy bundled with the packaged application. `compose.application.resources.dir`
        // points at the resources directory that jpackage created inside the application image.
        System.getProperty(RESOURCES_PROPERTY)
            ?.takeIf { it.isNotBlank() }
            ?.let { candidates.add(File(it, "vlc")) }

        val workingDirectory = System.getProperty("user.dir")
        if (!workingDirectory.isNullOrBlank()) {
            candidates.add(File(workingDirectory))
            candidates.add(File(workingDirectory, "vlc"))
        }

        val userHome = System.getProperty("user.home")
        if (!userHome.isNullOrBlank()) {
            candidates.add(File(userHome, ".gradle/vlcSetup/vlc"))
        }

        val localAppData = System.getenv("LOCALAPPDATA")
        if (!localAppData.isNullOrBlank()) {
            candidates.add(File(localAppData, "Programs/VLC"))
        }

        candidates.add(File("C:/Program Files/VLC"))
        candidates.add(File("C:/Program Files (x86)/VLC"))

        return candidates
    }
}


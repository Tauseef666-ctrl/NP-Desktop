/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import java.io.File

/**
 * Finds the libVLC installation and makes the native bindings load it from there.
 *
 * The lookup is done once per process. The following locations are checked, in this order:
 *
 * 1. the `newpipe.vlc.path` system property,
 * 2. the `NEWPIPE_VLC_PATH` environment variable,
 * 3. the working directory and its `vlc` subdirectory, which is where the packaged application
 *    keeps its copy of libVLC,
 * 4. the directory the Gradle `vlc-setup` plugin extracts libVLC to, which is used while
 *    developing,
 * 5. the default installation directories of VLC for Windows.
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
     * The name of the native library, which is the marker of a libVLC installation.
     */
    private const val LIBRARY_NAME = "libvlc.dll"

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

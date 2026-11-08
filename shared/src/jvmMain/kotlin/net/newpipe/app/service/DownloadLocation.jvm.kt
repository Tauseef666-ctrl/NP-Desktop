/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileSystemView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The desktop download folder: the user's choice is stored in the Java preferences, the fallback
 * is `Videos/NewPipe` in the user home.
 */
actual object DownloadLocation {

    private val prefs = Preferences.userNodeForPackage(DownloadLocation::class.java)
    private const val KEY = "download_path"

    actual var directory: String? = prefs.get(KEY, "").ifEmpty { null }
        set(value) {
            field = value
            if (value == null) {
                prefs.remove(KEY)
            } else {
                prefs.put(KEY, value)
            }
            prefs.flush()
        }

    actual fun resolve(): String =
        directory ?: File(System.getProperty("user.home"), "Videos${File.separator}NewPipe")
            .absolutePath

    /**
     * The desktop offers the Swing folder chooser.
     */
    actual val pickerSupported: Boolean = true

    actual suspend fun choose(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            var approved = false
            var picked: String? = null
            SwingUtilities.invokeAndWait {
                val chooser = JFileChooser(directory?.let { File(it) }).apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Choose download folder"
                    isAcceptAllFileFilterUsed = true
                }
                approved = chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
                if (approved) {
                    picked = chooser.selectedFile?.absolutePath
                }
            }
            val chosen = picked
            if (approved && chosen != null) {
                directory = chosen
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /**
     * A friendly display name of the folder for the user interface.
     */
    fun displayName(): String = runCatching {
        FileSystemView.getFileSystemView()
            .getSystemDisplayName(File(resolve()))
            .ifEmpty { resolve() }
    }.getOrDefault(resolve())
}

/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.di.settings

import co.touchlab.kermit.Logger
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.prefs.Preferences

/**
 * The file that stores the application settings of the desktop build.
 */
fun settingsFile(): File {
    val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("user.home") ?: "."
    return File(File(appData, "NP-Desktop"), "settings.properties")
}

/**
 * Copies every entry of the legacy `java.util.prefs` store into [target] once, so that users do
 * not lose their history, playlists or preferences after the storage migration.
 */
private fun migrateLegacyPreferences(target: Properties) {
    val legacy = try {
        Preferences.userRoot()
    } catch (exception: Throwable) {
        null
    }

    if (legacy == null) {
        return
    }

    try {
        var migrated = 0
        val keys: Array<String>? = legacy.keys()
        if (keys != null) {
            for (key in keys) {
                val value = legacy.get(key, null) ?: continue
                if (target.getProperty(key) == null) {
                    target.setProperty(key, value)
                    migrated++
                }
            }
        }
        if (migrated > 0) {
            Logger.i(messageString = "Migrated $migrated settings from java.util.prefs")
        }
    } catch (exception: Throwable) {
        Logger.w(messageString = "The old settings could not be migrated", throwable = exception)
    }
}

/**
 * A [Settings] that writes every change through to a properties file.
 *
 * `java.util.prefs` backs its store with the Windows registry, which rejects the large serialised
 * collections of the app and breaks when two instances run at once. This implementation keeps the
 * same key space in a plain file under the user data folder and stores it atomically after every
 * change.
 */
private class PersistingFileSettings(
    private val file: File,
    private val properties: Properties,
    private val base: Settings
) : Settings by base {

    private fun store() {
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(temporary).use { stream ->
                properties.store(stream, "NP-Desktop settings")
            }
            temporary.renameTo(file).let { replaced ->
                if (!replaced && !file.exists()) {
                    temporary.copyTo(file, overwrite = true)
                }
            }
        }.onFailure { exception ->
            Logger.w(messageString = "The settings could not be stored", throwable = exception)
        }
    }

    override fun putString(key: String, value: String) {
        base.putString(key, value)
        store()
    }

    override fun putInt(key: String, value: Int) {
        base.putInt(key, value)
        store()
    }

    override fun putLong(key: String, value: Long) {
        base.putLong(key, value)
        store()
    }

    override fun putFloat(key: String, value: Float) {
        base.putFloat(key, value)
        store()
    }

    override fun putDouble(key: String, value: Double) {
        base.putDouble(key, value)
        store()
    }

    override fun putBoolean(key: String, value: Boolean) {
        base.putBoolean(key, value)
        store()
    }

    override fun remove(key: String) {
        base.remove(key)
        store()
    }

    override fun clear() {
        base.clear()
        store()
    }
}

/**
 * File based settings for the desktop build.
 */
@Suppress("FunctionName")
fun desktopSettings(): Settings {
    val file = settingsFile()
    file.parentFile?.mkdirs()
    val properties = Properties()
    if (file.exists()) {
        runCatching {
            file.inputStream().use { properties.load(it) }
        }.onFailure { exception ->
            Logger.w(messageString = "The stored settings could not be read", throwable = exception)
        }
    } else {
        migrateLegacyPreferences(properties)
    }
    return PersistingFileSettings(file, properties, PropertiesSettings(properties))
}

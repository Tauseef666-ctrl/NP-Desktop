/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import java.io.File
import java.io.FileOutputStream
import java.util.Properties

/**
 * The YouTube web session of the desktop build.
 *
 * YouTube limits anonymous watch access from a network occasionally ("Sign in to confirm that
 * you're not a bot"). A signed-in web session removes that limit: the user pastes the cookies of
 * their browser session on youtube.com into the account settings, and the extraction engine sends
 * them with its requests. The store is a plain properties file under the user data folder and the
 * values are never printed to the logs.
 */
object YtSession {

    private val file: File
        get() {
            val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("user.home") ?: "."
            return File(File(appData, "NP-Desktop"), "yt-session.properties")
        }

    /** Cookie names that are relevant for the InnerTube playback flow. */
    private val RELEVANT_COOKIES = setOf(
        "SID", "HSID", "SSID", "APISID", "SAPISID",
        "__Secure-1PSID", "__Secure-1PSIDTS", "__Secure-1PAPISID",
        "__Secure-3PSID", "__Secure-3PSIDTS", "__Secure-3PAPISID",
        "LOGIN_INFO", "VISITOR_INFO1_LIVE", "SOCS", "PREF"
    )

    @Volatile
    private var cached: Map<String, String>? = null

    @Volatile
    private var cacheStamp = Long.MIN_VALUE

    /**
     * The parsed session cookies, reloaded from the file when it changed on disk.
     */
    fun cookies(): Map<String, String> {
        val current = file.takeIf { it.exists() }?.lastModified() ?: 0L
        val cache = cached
        if (cache != null && cacheStamp == current) {
            return cache
        }
        val loaded = if (current == 0L) {
            emptyMap<String, String>()
        } else {
            properties().entries.mapNotNull { entry ->
                val name = entry.key.toString().substringAfter('|', "")
                val value = entry.value?.toString()
                if (name.isBlank() || value.isNullOrBlank()) null else name to value
            }.toMap()
        }
        cacheStamp = current
        cached = loaded
        return loaded
    }

    /**
     * Whether a session is stored.
     */
    fun exists(): Boolean = cookies().isNotEmpty()

    /**
     * The most session cookie for the [Authorization] header, if the session provides one.
     */
    fun sapisid(): String? = cookies()["SAPISID"] ?: cookies()["__Secure-3PAPISID"]

    /**
     * Parses a pasted value as a raw `Cookie:` header or as a Netscape `cookies.txt` blob and
     * stores the relevant YouTube cookies. Returns the number of stored cookies.
     */
    fun save(paste: String): Int {
        val parsed = parsePaste(paste)
        if (parsed.isEmpty()) return 0

        val properties = Properties()
        for ((name, value) in parsed) {
            properties["youtube.com|$name"] = value
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temporary).use { stream ->
            properties.store(stream, "NP Desktop YouTube web session")
        }
        if (!temporary.renameTo(file) && !file.exists()) {
            temporary.copyTo(file, overwrite = true)
        }
        cached = null
        cacheStamp = Long.MIN_VALUE
        return parsed.size
    }

    /**
     * Clears the stored session.
     */
    fun signOut() {
        file.delete()
        cached = null
        cacheStamp = Long.MIN_VALUE
    }

    private fun properties(): Properties {
        val properties = Properties()
        runCatching { file.inputStream().use { properties.load(it) } }
        return properties
    }

    /**
     * Detects whether [paste] is a Netscape cookie file (`#` comments and tab separated rows) or a
     * raw cookie header, and returns the cookie name/value pairs.
     */
    private fun parsePaste(paste: String): Map<String, String> {
        val lines = paste.lines().map { it.trim() }
        val netscape = lines.filter { it.isNotBlank() && !it.startsWith("#") }
            .any { it.split("\t").size >= 6 }

        val result = linkedMapOf<String, String>()
        if (netscape) {
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val columns = line.split("\t")
                if (columns.size < 7) continue
                val domain = columns[0]
                if (!domain.contains("youtube.com")) continue
                val name = columns[5]
                val value = columns[6]
                if (name in RELEVANT_COOKIES) {
                    result[name] = value
                }
            }
        } else {
            val header = lines.joinToString(" ").trim()
                .removePrefix("Cookie:").trim().trim(';')
            for (pair in header.split(';')) {
                val separator = pair.indexOf('=')
                if (separator <= 0) continue
                val name = pair.substring(0, separator).trim()
                val value = pair.substring(separator + 1).trim()
                if (name in RELEVANT_COOKIES) {
                    result[name] = value
                }
            }
        }
        return result
    }

    /**
     * Builds the `SAPISIDHASH` value of the `Authorization` header for a request to the InnerTube
     * endpoints, the scheme YouTube's web client uses for signed-in users.
     */
    fun sapisidHash(): String? {
        val sapisid = sapisid() ?: return null
        val seconds = System.currentTimeMillis() / 1_000
        val hash = sha1Hex("https://www.youtube.com $sapisid $seconds")
        return "SAPISIDHASH ${seconds}_${hash}"
    }

    /**
     * Computes the lowercase SHA-1 hex digest of [value]; the derivation YouTube documents for
     * `SAPISIDHASH` requires SHA-1, so a stronger algorithm is not applicable here.
     */
    private fun sha1Hex(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}

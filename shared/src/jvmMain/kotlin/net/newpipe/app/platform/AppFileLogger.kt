/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes every kermit log line into a file inside the application data directory, so failures of
 * the packaged (installed) build can be diagnosed afterwards; the standard writers only reach the
 * process' standard error, which nobody sees for an installed desktop app.
 *
 * The file is capped: once [MAX_LOG_BYTES] is exceeded the current log is renamed to `*.old` and a
 * fresh one is started, so the directory never grows unboundedly.
 */
object AppFileLogger {

    private const val MAX_LOG_BYTES = 2_000_000L
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    private var file: File? = null

    /**
     * Registers the file writer on the global kermit logger. Safe to call repeatedly: a second
     * call is ignored. Every failure of the writer itself is swallowed, logging must never break
     * the app.
     */
    fun install() {
        synchronized(this) {
            if (file != null) {
                return
            }
            file = try {
                val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                    ?: System.getProperty("user.home") ?: return
                val directory = File(File(appData, "NP-Desktop"), "logs")
                if (!directory.isDirectory && !directory.mkdirs()) {
                    return
                }
                File(directory, "npdesktop.log")
            } catch (_: Throwable) {
                return
            }
        }
        Logger.addLogWriter(object : LogWriter() {
            override fun log(
                severity: Severity,
                message: String,
                tag: String,
                throwable: Throwable?
            ) {
                write(severity, tag, message, throwable)
            }
        })
    }

    /** The log file that [install] writes to, or `null` when it could not be set up. */
    fun logFile(): File? = file

    private fun write(severity: Severity, tag: String?, message: String, throwable: Throwable?) {
        val target = file ?: return
        try {
            synchronized(this) {
                rotateWhenFull(target)
                target.appendText(buildString {
                    append(timeFormat.format(Date()))
                    append("  ")
                    append(severity.name.first())
                    append("  ")
                    if (!tag.isNullOrBlank()) {
                        append(tag)
                        append(": ")
                    }
                    append(message)
                    if (throwable != null) {
                        append('\n')
                        val stack = StringWriter()
                        throwable.printStackTrace(PrintWriter(stack))
                        append(stack.toString().trimEnd())
                    }
                    append('\n')
                })
            }
        } catch (_: Throwable) {
            // Never let the log writer break the app.
        }
    }

    private fun rotateWhenFull(target: File) {
        try {
            if (target.length() <= MAX_LOG_BYTES) {
                return
            }
            val previous = File(target.parentFile, target.name + ".old")
            if (previous.isFile) {
                previous.delete()
            }
            target.renameTo(previous)
        } catch (_: Throwable) {
        }
    }
}

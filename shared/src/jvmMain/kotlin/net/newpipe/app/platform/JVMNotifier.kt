/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import java.awt.Color
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import org.koin.core.annotation.Singleton

/**
 * Desktop [Notifier] backed by the AWT system tray. Delivery is honest: [isSupported] reflects
 * {@link SystemTray#isSupported()}, and [notify] returns `false` (never throws) when the tray is
 * unavailable or the platform rejects the call, so callers never claim a notification was shown.
 *
 * The tray icon is created lazily on first successful [notify] and reused thereafter.
 */
@Singleton(binds = [Notifier::class])
class JVMNotifier : Notifier {

    override val isSupported: Boolean
        get() = SystemTray.isSupported()

    private var trayIcon: TrayIcon? = null

    override fun notify(title: String, body: String): Boolean {
        if (!isSupported) return false
        return try {
            val icon = trayIcon ?: createTrayIcon().also {
                SystemTray.getSystemTray().add(it)
                trayIcon = it
            }
            icon.displayMessage(title, body, TrayIcon.MessageType.INFO)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun createTrayIcon(): TrayIcon = TrayIcon(appIcon()).apply {
        isImageAutoSize = true
        setToolTip("NewPipe+")
    }

    /** A tiny near-black tile with the NewPipe+ red play glyph; no bundled raster asset needed. */
    private fun appIcon(): Image {
        val size = 16
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setColor(Color(0xFF0F0F12.toInt()))
            g.fillRect(0, 0, size, size)
            g.setColor(Color(0xFFE53935.toInt()))
            g.fillPolygon(intArrayOf(4, 12, 4), intArrayOf(4, 8, 12), 3)
        } finally {
            g.dispose()
        }
        return image
    }
}

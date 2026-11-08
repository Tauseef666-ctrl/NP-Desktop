/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import net.newpipe.app.di.settings.desktopSettings

/**
 * The geometry of the popup player window saved between sessions.
 */
data class PopupWindowBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val maximized: Boolean,
    val hasPosition: Boolean = true
)

private const val POPUP_PREFIX = "popup.window."

/**
 * Reads the place and size the user last left the popup player window at, or `null` when no
 * geometry was saved yet.
 */
fun loadPopupWindowBounds(): PopupWindowBounds? {
    return runCatching {
        val settings = desktopSettings()
        val width = settings.getFloat("${POPUP_PREFIX}width", 0f)
        val height = settings.getFloat("${POPUP_PREFIX}height", 0f)
        val hasPosition = settings.getBoolean("${POPUP_PREFIX}positioned", false)
        val x = settings.getFloat("${POPUP_PREFIX}x", 0f)
        val y = settings.getFloat("${POPUP_PREFIX}y", 0f)
        if (width < 80f || height < 60f) {
            null
        } else {
            PopupWindowBounds(
                x = x,
                y = y,
                width = width,
                height = height,
                maximized = settings.getString("${POPUP_PREFIX}placement", "") == "maximized",
                hasPosition = hasPosition
            )
        }
    }.getOrNull()
}

/**
 * Writes the current geometry of the popup player window into the desktop settings, so the next
 * popup opens on the exact place and with the size the user left it at.
 */
fun savePopupWindowBounds(bounds: PopupWindowBounds) {
    runCatching {
        val settings = desktopSettings()
        settings.putFloat("${POPUP_PREFIX}width", bounds.width)
        settings.putFloat("${POPUP_PREFIX}height", bounds.height)
        settings.putString(
            "${POPUP_PREFIX}placement",
            if (bounds.maximized) "maximized" else "floating"
        )
        settings.putBoolean("${POPUP_PREFIX}positioned", bounds.hasPosition)
        settings.putFloat("${POPUP_PREFIX}x", bounds.x)
        settings.putFloat("${POPUP_PREFIX}y", bounds.y)
    }
}

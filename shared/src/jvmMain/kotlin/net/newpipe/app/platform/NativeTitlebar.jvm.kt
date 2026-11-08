/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

private interface User32Lib : Library {
    /** `null` class name means "any class", the title selects the window. */
    fun FindWindowA(className: String?, title: String?): Pointer?
}

private interface DwmLib : Library {
    fun DwmSetWindowAttribute(
        hwnd: Pointer?,
        attribute: Int,
        value: IntByReference?,
        size: Int
    ): Int
}

/** Attribute of the Windows DWM API that turns on the immersive (dark) title bar. */
private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

/** Attributes painting the Windows 11 caption/border chrome. Harmless no-ops on Windows 10. */
private const val DWMWA_CAPTION_COLOR = 35
private const val DWMWA_BORDER_COLOR = 34

/** Near-black used for the caption, matching the app background. */
private const val APP_CAPTION_COLOR = 0x001E1E1E

actual fun applyDarkTitlebar(windowTitle: String): Boolean {
    if (windowTitle.isBlank()) return false
    return runCatching {
        val user32 = Native.load("user32", User32Lib::class.java)
        val dwm = Native.load("dwmapi", DwmLib::class.java)
        val hwnd = user32.FindWindowA(null, windowTitle) ?: return false

        // The immersive dark mode flag drives the classic Win10/11 dark chrome; the explicit
        // caption/border colors make sure the frame reads black. Errors from older Windows
        // builds are ignored on purpose - the flag is enough there.
        dwm.DwmSetWindowAttribute(
            hwnd,
            DWMWA_USE_IMMERSIVE_DARK_MODE,
            IntByReference(1),
            4
        )
        dwm.DwmSetWindowAttribute(
            hwnd,
            DWMWA_CAPTION_COLOR,
            IntByReference(APP_CAPTION_COLOR),
            4
        )
        dwm.DwmSetWindowAttribute(
            hwnd,
            DWMWA_BORDER_COLOR,
            IntByReference(APP_CAPTION_COLOR),
            4
        )
        true
    }.getOrDefault(false)
}

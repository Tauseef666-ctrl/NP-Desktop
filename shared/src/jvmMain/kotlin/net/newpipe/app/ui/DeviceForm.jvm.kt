/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

/**
 * The desktop build (Windows, Linux) is laid out for pointer input by default.
 *
 * A machine connected to a television can still force the TV layout: set the environment
 * variable `NP_FORM` or the Java system property `newpipe.form` to `tv`. The default stays
 * [DeviceForm.DESKTOP], so the regular Windows behavior never changes unless the override
 * is present.
 */
actual val currentDeviceForm: DeviceForm = run {
    val override = (
        System.getProperty("newpipe.form")
            ?: System.getenv("NP_FORM")
        )?.trim()?.lowercase()
    when (override) {
        "tv" -> DeviceForm.TV
        else -> DeviceForm.DESKTOP
    }
}

/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

/**
 * The form factor the user interface is laid out for.
 *
 * The desktop build runs as [DESKTOP] by default and can be switched to [TV] with the
 * `NP_FORM` environment variable or the `newpipe.form` system property (a PC connected to a
 * television, or manual testing of the TV layout). The Android build decides per device: an
 * Android TV box, a Fire TV or a set top box reports [TV]; phones, tablets and foldables
 * report [DESKTOP]. The shell uses the value to choose between hover driven navigation and a
 * permanently visible, focusable rail.
 */
enum class DeviceForm {
    DESKTOP,
    TV
}

/**
 * The platform specific layout decision; see [DeviceForm].
 */
expect val currentDeviceForm: DeviceForm

/**
 * Whether the shell should be laid out for a remote controlled television.
 */
val isTvLayout: Boolean
    get() = currentDeviceForm == DeviceForm.TV

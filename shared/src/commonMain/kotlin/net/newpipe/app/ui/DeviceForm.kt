/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

/**
 * The form factor the user interface is laid out for.
 *
 * The desktop build always runs as [DESKTOP], the Android build decides per device: an Android TV
 * box or a Fire TV reports [TV], phones, tablets and foldables report [DESKTOP]. The shell uses
 * the value to choose between hover driven navigation and a permanently visible, focusable rail.
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

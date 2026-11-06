/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

/**
 * The desktop build (Windows, Linux) is always laid out for pointer input.
 */
actual val currentDeviceForm: DeviceForm = DeviceForm.DESKTOP

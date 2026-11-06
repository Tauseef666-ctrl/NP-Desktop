/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

/**
 * Detects and holds the device form of the Android build.
 *
 * The activity calls [detect] once at start up; the shell then reads [value].
 */
object AndroidDeviceForm {

    @Volatile
    var value: DeviceForm = DeviceForm.DESKTOP
        private set

    /**
     * A device counts as a television when the ui mode type is `television` or the device declares
     * the leanback feature without a touchscreen.
     */
    fun detect(context: Context): DeviceForm {
        val configuration = context.resources?.configuration
        val uiModeType = configuration?.uiMode?.and(Configuration.UI_MODE_TYPE_MASK) ?: 0
        val leanback = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)
        }.getOrDefault(false)

        value = if (uiModeType == UI_MODE_TYPE_TELEVISION || leanback) {
            DeviceForm.TV
        } else {
            DeviceForm.DESKTOP
        }
        return value
    }
}

/**
 * The Android build distinguishes between televisions and touch devices per configuration.
 */
actual val currentDeviceForm: DeviceForm
    get() = AndroidDeviceForm.value

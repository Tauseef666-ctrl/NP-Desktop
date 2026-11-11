/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import android.os.BatteryManager
import android.os.Build

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
     * A device counts as a television when the ui mode type is `television`, when it declares
     * the leanback feature, when the model identifies an Amazon Fire TV, or when it is a
     * set top box without touchscreen and battery. The heuristics mirror the legacy
     * `DeviceUtils.isTv` so both apps classify the same devices the same way.
     */
    fun detect(context: Context): DeviceForm {
        val configuration = context.resources?.configuration
        val uiModeType = configuration?.uiMode?.and(Configuration.UI_MODE_TYPE_MASK) ?: 0
        val packageManager = context.packageManager
        val leanback = runCatching {
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)
        }.getOrDefault(false)
        val fireTv = Build.MODEL?.uppercase()?.let { model ->
            model.startsWith("AFT") || model.contains("FIRE TV")
        } == true
        val batteryAbsent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) == false
        }.getOrDefault(false)
        val touchscreenMissing = runCatching {
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        }.getOrDefault(false)

        value = if (
            uiModeType == UI_MODE_TYPE_TELEVISION ||
            leanback ||
            fireTv ||
            (batteryAbsent && touchscreenMissing)
        ) {
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

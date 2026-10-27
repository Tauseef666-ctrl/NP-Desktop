/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.di.settings

import com.russhwolf.settings.Settings
import org.koin.core.annotation.Singleton

/**
 * Settings for the desktop build: a reliable file store under `%APPDATA%/NP-Desktop`.
 */
@Singleton
fun provideSettings(): Settings = desktopSettings()

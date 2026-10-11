/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

/**
 * Koin module for the shared Backup & Restore infrastructure ([BackupContent] and the platform
 * [BackupRepository] implementation living in this package).
 */
@Module
@ComponentScan
@Configuration
object BackupModule

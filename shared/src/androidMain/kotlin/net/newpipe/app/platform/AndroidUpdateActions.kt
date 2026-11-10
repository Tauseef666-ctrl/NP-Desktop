/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

/**
 * Android has no in-app update flow: the releases and the store handle updates, and the update
 * category is a silent no-op there.
 */
@Singleton(binds = [UpdateActions::class])
class AndroidUpdateActions : UpdateActions by NoOpUpdateActions

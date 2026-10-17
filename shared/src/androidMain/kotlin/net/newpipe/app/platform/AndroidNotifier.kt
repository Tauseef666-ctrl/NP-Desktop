/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

/**
 * Android [Notifier] placeholder. Desktop notifications are the port's target; the Android target
 * only satisfies the Koin graph and honestly reports [isSupported] `false` so the shared UI shows an
 * unavailable state rather than pretending to notify.
 */
@Singleton(binds = [Notifier::class])
class AndroidNotifier : Notifier {
    override val isSupported: Boolean = false
    override fun notify(title: String, body: String): Boolean = false
}

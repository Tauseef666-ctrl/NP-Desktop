/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.subscriptions

import net.newpipe.app.model.ContentItem

/**
 * Session cache of the built subscriptions feed, so revisiting the screen renders instantly
 * instead of refetching every subscribed channel with a spinner.
 */
object SubscriptionsFeedCache {
    var feed: List<ContentItem> = emptyList()
    var channelKey: String = ""
    var fetchedAt: Long = 0L
}

/**
 * How long the cached subscriptions feed is reused before a fresh build happens.
 */
const val SUBSCRIPTIONS_FEED_TTL_MS = 10 * 60_000L

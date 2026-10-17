/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

/**
 * One channel's slice of the subscription feed: the channel label plus the (capped) list of its
 * newest items. Kept provider-agnostic so any provider or test fake maps into it.
 */
data class SubscriptionFeedGroup(
    val channelName: String,
    val channelUrl: String,
    val items: List<ProviderContentItem>
)

/**
 * The assembled subscription feed: the per-channel [groups] that actually had content, and the
 * names of the channels that could not be loaded ([failedChannels]) so the UI can report them
 * honestly instead of hiding the failure.
 */
data class SubscriptionFeed(
    val groups: List<SubscriptionFeedGroup>,
    val failedChannels: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = groups.isEmpty()

    val itemCount: Int get() = groups.sumOf { it.items.size }
}

/**
 * The result of loading a single channel: its display name/url and the items it returned. A channel
 * that failed is not represented here — it goes into [SubscriptionFeed.failedChannels] instead.
 */
data class ChannelFetch(
    val channelName: String,
    val channelUrl: String,
    val items: List<ProviderContentItem>
)

/**
 * Pure assembly of the subscription feed from already-fetched channels.
 *
 * Channels with no items are dropped (an empty group would just be visual noise), and each group is
 * capped to [perChannel] newest items ([perChannel] `<= 0` keeps them all). The order of [fetches]
 * is preserved, so the caller controls the channel ordering.
 */
fun buildSubscriptionFeed(
    fetches: List<ChannelFetch>,
    failedChannels: List<String> = emptyList(),
    perChannel: Int = DEFAULT_PER_CHANNEL
): SubscriptionFeed {
    val groups = fetches.mapNotNull { fetch ->
        val items = if (perChannel > 0) fetch.items.take(perChannel) else fetch.items
        if (items.isEmpty()) {
            null
        } else {
            SubscriptionFeedGroup(
                channelName = fetch.channelName,
                channelUrl = fetch.channelUrl,
                items = items
            )
        }
    }
    return SubscriptionFeed(groups = groups, failedChannels = failedChannels)
}

/** Number of newest items shown per channel by default. */
const val DEFAULT_PER_CHANNEL: Int = 10

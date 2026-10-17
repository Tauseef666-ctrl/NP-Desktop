/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionFeedTest {

    private fun item(id: String) = ProviderContentItem(id = id, title = id, url = "https://v/$id")

    private fun fetch(name: String, count: Int) = ChannelFetch(
        channelName = name,
        channelUrl = "https://c/$name",
        items = (1..count).map { item("$name-$it") }
    )

    @Test
    fun groupsAreBuiltPerChannelInOrder() {
        val feed = buildSubscriptionFeed(listOf(fetch("A", 2), fetch("B", 1)))

        assertEquals(listOf("A", "B"), feed.groups.map { it.channelName })
        assertEquals(3, feed.itemCount)
    }

    @Test
    fun emptyChannelsAreDropped() {
        val feed = buildSubscriptionFeed(listOf(fetch("A", 0), fetch("B", 2)))

        assertEquals(listOf("B"), feed.groups.map { it.channelName })
    }

    @Test
    fun perChannelCapsItems() {
        val feed = buildSubscriptionFeed(listOf(fetch("A", 5)), perChannel = 2)

        assertEquals(2, feed.groups.single().items.size)
        assertEquals(listOf("A-1", "A-2"), feed.groups.single().items.map { it.id })
    }

    @Test
    fun nonPositivePerChannelKeepsAll() {
        val feed = buildSubscriptionFeed(listOf(fetch("A", 5)), perChannel = 0)

        assertEquals(5, feed.groups.single().items.size)
    }

    @Test
    fun failedChannelsAreReported() {
        val feed = buildSubscriptionFeed(
            fetches = listOf(fetch("A", 1)),
            failedChannels = listOf("B", "C")
        )

        assertEquals(listOf("B", "C"), feed.failedChannels)
    }

    @Test
    fun allEmptyIsAnEmptyFeedButKeepsFailedChannels() {
        val feed = buildSubscriptionFeed(listOf(fetch("A", 0)), failedChannels = listOf("B"))

        assertTrue(feed.isEmpty)
        assertEquals(0, feed.itemCount)
        assertEquals(listOf("B"), feed.failedChannels)
    }

    @Test
    fun noFetchesIsEmptyFeed() {
        val feed = buildSubscriptionFeed(emptyList())

        assertTrue(feed.isEmpty)
        assertFalse(feed.groups.any { it.items.isEmpty() })
    }
}

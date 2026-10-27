/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.newpipe.app.model.SubscribedChannel

/**
 * Stores the channels the user subscribed to, most recent first.
 *
 * Subscriptions are local because the extraction engine has read-only access to the services. The
 * list is persisted as JSON in the platform settings.
 */
class SubscriptionRepository(
    private val settings: Settings
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _subscriptions = MutableStateFlow(load())
    val subscriptions = _subscriptions.asStateFlow()

    /**
     * Returns `true` when the channel with the given url is subscribed.
     */
    fun isSubscribed(url: String?): Boolean =
        url != null && _subscriptions.value.any { it.url == url }

    /**
     * Subscribes to the channel, or unsubscribes when it is already subscribed.
     *
     * @return `true` when the channel is subscribed afterwards
     */
    fun toggle(channel: SubscribedChannel): Boolean {
        val subscribed = isSubscribed(channel.url)
        if (subscribed) {
            remove(channel.url)
        } else {
            add(channel)
        }
        return !subscribed
    }

    /**
     * Adds the channel to the front of the subscription list.
     */
    fun add(channel: SubscribedChannel) {
        if (channel.url.isBlank()) return
        val updated = (listOf(channel) + _subscriptions.value.filterNot { it.url == channel.url })
        _subscriptions.value = updated
        persist(updated)
    }

    /**
     * Removes the channel with the given url from the subscription list.
     */
    fun remove(url: String) {
        val updated = _subscriptions.value.filterNot { it.url == url }
        if (updated.size == _subscriptions.value.size) return
        _subscriptions.value = updated
        persist(updated)
    }

    /**
     * Removes all subscriptions.
     */
    fun clear() {
        _subscriptions.value = emptyList()
        settings.remove(KEY)
    }

    private fun load(): List<SubscribedChannel> {
        val stored = settings.getStringOrNull(KEY) ?: return emptyList()
        return try {
            json.decodeFromString<List<StoredSubscription>>(stored).map { it.toChannel() }
        } catch (exception: Exception) {
            Logger.w(
                messageString = "The subscriptions could not be read",
                throwable = exception
            )
            emptyList()
        }
    }

    private fun persist(channels: List<SubscribedChannel>) {
        try {
            val stored = channels.map { StoredSubscription.from(it) }
            settings.putString(KEY, json.encodeToString(stored))
        } catch (exception: Exception) {
            Logger.w(
                messageString = "The subscriptions could not be saved",
                throwable = exception
            )
        }
    }

    @Serializable
    private data class StoredSubscription(
        val serviceId: Int,
        val url: String,
        val name: String,
        val avatarUrl: String? = null
    ) {
        fun toChannel() = SubscribedChannel(
            serviceId = serviceId,
            url = url,
            name = name,
            avatarUrl = avatarUrl
        )

        companion object {
            fun from(channel: SubscribedChannel) = StoredSubscription(
                serviceId = channel.serviceId,
                url = channel.url,
                name = channel.name,
                avatarUrl = channel.avatarUrl
            )
        }
    }

    companion object {
        private const val KEY = "subscriptions_v1"
    }
}

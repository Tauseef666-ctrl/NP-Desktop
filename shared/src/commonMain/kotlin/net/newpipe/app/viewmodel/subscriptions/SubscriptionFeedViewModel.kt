/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.provider.ChannelFetch
import net.newpipe.app.provider.DEFAULT_PER_CHANNEL
import net.newpipe.app.provider.MediaPlayback
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderPlaybackSession
import net.newpipe.app.provider.ProviderResult
import net.newpipe.app.provider.SubscriptionFeed
import net.newpipe.app.provider.buildSubscriptionFeed
import net.newpipe.app.subscription.SubscriptionStore
import org.koin.core.annotation.KoinViewModel

/**
 * Builds the provider-backed subscription feed: for every locally-managed subscription it asks the
 * content repository for that channel's newest videos and assembles them per channel.
 *
 * It is deliberately honest about partial failure: if some channels load, the feed still renders and
 * the failed channel names are reported; only when *every* channel fails (and none produced content)
 * does it surface an unavailable state. An empty subscription list yields an empty (not an error)
 * feed, so the screen shows its empty state.
 */
@KoinViewModel
class SubscriptionFeedViewModel(
    store: SubscriptionStore,
    private val repository: ProviderContentRepository,
    private val mediaPlayback: MediaPlayback
) : ViewModel() {

    /** State of the subscription feed. */
    sealed interface FeedState {
        /** Nothing requested yet. */
        data object Idle : FeedState

        /** A load is in flight. */
        data object Loading : FeedState

        /** A feed was assembled (possibly empty, possibly with failed channels). */
        data class Content(val feed: SubscriptionFeed) : FeedState

        /** No channel could be loaded; [reason] explains why. */
        data class Unavailable(val reason: ProviderFailureReason) : FeedState
    }

    private val subscriptions = store.entries

    private val playbackSession = ProviderPlaybackSession(mediaPlayback, viewModelScope)

    /** Observable playback-request state for feed items. */
    val playbackState = playbackSession.state

    private val _state = MutableStateFlow<FeedState>(FeedState.Idle)

    /** Observable feed state. */
    val state: StateFlow<FeedState> = _state.asStateFlow()

    /**
     * Load the newest videos of every subscription, capped to [perChannel] items each. Collapses
     * overlapping calls by ignoring a request while one is already in flight.
     */
    fun load(perChannel: Int = DEFAULT_PER_CHANNEL) {
        if (_state.value == FeedState.Loading) return
        val subs = subscriptions.value
        _state.value = FeedState.Loading
        if (subs.isEmpty()) {
            _state.value = FeedState.Content(SubscriptionFeed(groups = emptyList()))
            return
        }
        viewModelScope.launch {
            val fetches = mutableListOf<ChannelFetch>()
            val failed = mutableListOf<String>()
            var lastReason: ProviderFailureReason? = null
            for (sub in subs) {
                when (val result = repository.channel(sub.url)) {
                    is ProviderResult.Success -> fetches += ChannelFetch(
                        channelName = sub.name.ifBlank { sub.url },
                        channelUrl = sub.url,
                        items = result.value.items
                    )
                    is ProviderResult.Failure -> {
                        failed += sub.name.ifBlank { sub.url }
                        lastReason = result.reason
                    }
                }
            }
            _state.value = if (fetches.isEmpty() && lastReason != null) {
                FeedState.Unavailable(lastReason)
            } else {
                FeedState.Content(buildSubscriptionFeed(fetches, failed, perChannel))
            }
        }
    }

    /** Play the feed [item] through the shared provider playback session. */
    fun play(item: ProviderContentItem) = playbackSession.play(item)

    /** Play every [item] of one feed group, starting from the first playable one. */
    fun playAll(items: List<ProviderContentItem>) = playbackSession.playAll(items)

    /** Reset to [FeedState.Idle]; used when the screen is left. */
    fun reset() {
        _state.value = FeedState.Idle
    }
}

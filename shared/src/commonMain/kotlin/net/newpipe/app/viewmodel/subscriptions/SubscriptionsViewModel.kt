/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.subscriptions

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.subscription.Subscription
import net.newpipe.app.subscription.SubscriptionStore
import net.newpipe.app.subscription.normalizeChannelUrl
import net.newpipe.app.subscription.subscriptionIdFor
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the local subscriptions screen: lists the channels the user added by hand, validates new
 * URLs and removes existing entries. No streaming provider is bundled yet, so no video data is
 * produced here.
 */
@KoinViewModel
class SubscriptionsViewModel(private val store: SubscriptionStore) : ViewModel() {

    /** Subscriptions, mirroring [SubscriptionStore.entries]. */
    val subscriptions: StateFlow<List<Subscription>> = store.entries

    /** Outcome of [add], surfaced to the add dialog for inline validation feedback. */
    sealed interface AddResult {
        data object Added : AddResult
        data object InvalidUrl : AddResult
        data object Duplicate : AddResult
    }

    /**
     * Validate [url] and add it as a subscription. Returns [AddResult.InvalidUrl] when the URL cannot
     * be normalized, [AddResult.Duplicate] when it is already subscribed, otherwise [AddResult.Added].
     * A blank [name] falls back to the normalized URL.
     */
    fun add(name: String, url: String): AddResult {
        val id = subscriptionIdFor(url) ?: return AddResult.InvalidUrl
        if (store.contains(id)) return AddResult.Duplicate
        val normalized = normalizeChannelUrl(url) ?: return AddResult.InvalidUrl
        val displayName = name.trim().ifBlank { normalized }
        store.add(Subscription(id = id, name = displayName, url = normalized))
        return AddResult.Added
    }

    /** Remove the subscription with [id]. */
    fun remove(id: String) = store.remove(id)
}

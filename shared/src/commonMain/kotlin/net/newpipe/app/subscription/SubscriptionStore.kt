/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.subscription

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Singleton

/**
 * One locally-managed channel subscription. [id] is the normalized channel URL used for de-duplication;
 * [name] is the user-facing label (falling back to [url]), [url] is the normalized channel URL and
 * [addedAtEpochMs] is when it was added.
 */
@Serializable
data class Subscription(
    val id: String,
    val name: String = "",
    val url: String = "",
    val addedAtEpochMs: Long = 0L
)

/** Serializable snapshot of the whole subscription list. */
@Serializable
data class SubscriptionSnapshot(
    val items: List<Subscription> = emptyList()
)

/**
 * Persists the user-editable channel subscriptions (newest first, capped at [MAX_ENTRIES]). Mirrors
 * [net.newpipe.app.player.RecentlyPlayedStore] storage conventions: a blank or corrupt blob decodes
 * to an empty list rather than throwing. No streaming provider is bundled yet, so this store holds
 * only what the user entered and never fabricates videos.
 *
 * [entries] is a hot [StateFlow] so the UI updates the instant a subscription is added or removed.
 */
@Singleton
class SubscriptionStore(
    private val settings: Settings,
    private val json: Json
) {

    private val key = KEY

    private val _entries = MutableStateFlow(loadPersisted())
    val entries: StateFlow<List<Subscription>> = _entries.asStateFlow()

    /**
     * Add [subscription] as the newest entry, stamping it with [nowEpochMs]. A blank or already-present
     * id is ignored (no-op), and the list is trimmed to [MAX_ENTRIES].
     */
    fun add(subscription: Subscription, nowEpochMs: Long = System.currentTimeMillis()) {
        val updated = pureAdd(_entries.value, subscription, nowEpochMs, MAX_ENTRIES)
        if (updated == _entries.value) return
        _entries.value = updated
        persist(updated)
    }

    /** Remove the subscription with [id]; other entries are untouched. */
    fun remove(id: String) {
        val updated = _entries.value.filterNot { it.id == id }
        if (updated.size == _entries.value.size) return
        _entries.value = updated
        persist(updated)
    }

    /** Remove every subscription. */
    fun clear() {
        _entries.value = emptyList()
        settings.remove(key)
    }

    /** Whether a subscription with [id] is currently stored. */
    fun contains(id: String): Boolean = _entries.value.any { it.id == id }

    private fun loadPersisted(): List<Subscription> {
        val raw = settings.getStringOrNull(key) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<SubscriptionSnapshot>(raw).items
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun persist(items: List<Subscription>) {
        settings.putString(key, json.encodeToString(SubscriptionSnapshot(items)))
    }

    companion object {
        const val KEY = "npp_subscriptions"

        /** Maximum number of subscriptions kept; older entries fall off the end. */
        const val MAX_ENTRIES = 500

        /**
         * Pure add used by [add]: ignores blank ids, treats a duplicate id as a no-op (returns [items]
         * unchanged), stamps [nowEpochMs], prepends the fresh entry and trims to [cap]. Extracted so
         * the ordering and de-duplication rules are unit-testable.
         */
        fun pureAdd(
            items: List<Subscription>,
            subscription: Subscription,
            nowEpochMs: Long,
            cap: Int
        ): List<Subscription> {
            if (subscription.id.isBlank()) return items
            if (items.any { it.id == subscription.id }) return items
            val entry = subscription.copy(addedAtEpochMs = nowEpochMs)
            val limited = if (cap < 0) 0 else cap
            return (listOf(entry) + items).take(limited)
        }
    }
}

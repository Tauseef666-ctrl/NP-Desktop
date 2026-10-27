/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Destinations for navigation in compose
 */
@Serializable
sealed interface Destination : NavKey {

    @Serializable
    data object DownloadSettings : Destination

    @Serializable
    data object DebugSettings : Destination

    @Serializable
    data object PlayerSettings : Destination

    @Serializable
    data object VideoAudioSettings : Destination

    @Serializable
    data object AppearanceSettings : Destination

    @Serializable
    data object AccountSettings : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object About : Destination

    /**
     * The home screen: the predefined content lists of a service, for example trending.
     */
    @Serializable
    data object Home : Destination

    /**
     * A single content list ("kiosk") of the service, for example Trending, Live or Gaming.
     *
     * @param kioskId the identifier of the kiosk in the extraction engine
     * @param title the label shown to the user
     */
    @Serializable
    data class Feed(val kioskId: String, val title: String) : Destination

    /**
     * The search screen.
     */
    @Serializable
    data object Search : Destination

    /**
     * The videos that were downloaded to the local storage.
     */
    @Serializable
    data object Downloads : Destination

    /**
     * The watch history.
     */
    @Serializable
    data object History : Destination

    /**
     * The channels the user subscribed to, together with their latest uploads.
     */
    @Serializable
    data object Subscriptions : Destination

    /**
     * The details of a single stream.
     *
     * @param serviceId the id of the service the stream belongs to
     * @param url the canonical url of the stream
     */
    @Serializable
    data class Stream(val serviceId: Int, val url: String) : Destination

    /**
     * The details of a single channel.
     *
     * @param serviceId the id of the service the channel belongs to
     * @param url the canonical url of the channel
     */
    @Serializable
    data class Channel(val serviceId: Int, val url: String) : Destination

    /**
     * The details of a single playlist.
     *
     * @param serviceId the id of the service the playlist belongs to
     * @param url the canonical url of the playlist
     */
    @Serializable
    data class Playlist(val serviceId: Int, val url: String) : Destination
}

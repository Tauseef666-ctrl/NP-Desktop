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
    data object Home : Destination

    @Serializable
    data object Player : Destination

    @Serializable
    data object Library : Destination

    @Serializable
    data object Downloads : Destination

    @Serializable
    data object History : Destination

    @Serializable
    data object Search : Destination

    @Serializable
    data object Trending : Destination

    @Serializable
    data object PlayerSettings : Destination

    @Serializable
    data object VideoAudioSettings : Destination

    @Serializable
    data object AppearanceSettings : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object BackupRestore : Destination

    @Serializable
    data object About : Destination
}


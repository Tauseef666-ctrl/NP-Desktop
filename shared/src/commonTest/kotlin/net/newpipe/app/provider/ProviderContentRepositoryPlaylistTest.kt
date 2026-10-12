/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class PlaylistStubProvider(
    override val id: String = "playlist-stub",
    override val name: String = "Playlist stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(playlists = true),
    private val onPlaylist: suspend (url: String, pageToken: String?) -> ProviderResult<ProviderContentPage> =
        { _, _ -> ProviderResult.Success(ProviderContentPage(emptyList())) }
) : MediaProvider {

    var lastUrl: String? = null
        private set

    var receivedPageToken: String? = null
        private set

    var playlistCalled: Boolean = false
        private set

    var playlistCalls: Int = 0
        private set

    override suspend fun playlist(url: String, pageToken: String?): ProviderResult<ProviderContentPage> {
        lastUrl = url
        receivedPageToken = pageToken
        playlistCalled = true
        playlistCalls++
        return onPlaylist(url, pageToken)
    }
}

class ProviderContentRepositoryPlaylistTest {

    private fun repository(vararg providers: MediaProvider): ProviderContentRepository {
        val registry = ProviderRegistry()
        providers.forEach { registry.register(it) }
        return ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined }
    }

    @Test
    fun playlistWithCapabilityReturnsSuccessFromProvider() = runTest {
        val page = ProviderContentPage(
            items = listOf(
                ProviderContentItem(id = "v1", title = "First", url = "https://page/1"),
                ProviderContentItem(id = "v2", title = "Second", url = "https://page/2")
            ),
            nextPageToken = "next"
        )
        val repository = repository(
            PlaylistStubProvider(onPlaylist = { _, _ -> ProviderResult.Success(page) })
        )

        assertEquals(
            ProviderResult.Success(page),
            repository.playlist("https://playlist")
        )
    }

    @Test
    fun playlistWithNoProviderFailsWithNoProvider() = runTest {
        val repository = repository()

        assertEquals(
            ProviderResult.Failure(ProviderFailureReason.NO_PROVIDER),
            repository.playlist("https://playlist")
        )
    }

    @Test
    fun playlistWithoutPlaylistsCapabilityFailsUnsupported() = runTest {
        val repository = repository(
            PlaylistStubProvider(
                capabilities = ProviderCapabilities(),
                onPlaylist = { _, _ ->
                    ProviderResult.Success(
                        ProviderContentPage(
                            listOf(ProviderContentItem(id = "v1", title = "First", url = "https://page/1"))
                        )
                    )
                }
            )
        )

        assertEquals(
            ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED),
            repository.playlist("https://playlist")
        )
    }

    @Test
    fun playlistForwardsPageTokenToProvider() = runTest {
        val provider = PlaylistStubProvider(
            onPlaylist = { _, _ -> ProviderResult.Success(ProviderContentPage(emptyList())) }
        )
        val repository = repository(provider)

        repository.playlist("https://playlist", pageToken = "page-2")

        assertTrue(provider.playlistCalled)
        assertEquals("page-2", provider.receivedPageToken)
        assertEquals("https://playlist", provider.lastUrl)
    }

    @Test
    fun playlistPropagatesProviderFailureReason() = runTest {
        val repository = repository(
            PlaylistStubProvider(
                onPlaylist = { _, _ -> ProviderResult.Failure(ProviderFailureReason.NETWORK) }
            )
        )

        assertEquals(
            ProviderResult.Failure(ProviderFailureReason.NETWORK),
            repository.playlist("https://playlist")
        )
    }

    @Test
    fun playlistMapsThrownExceptionToUnknown() = runTest {
        val repository = repository(
            PlaylistStubProvider(onPlaylist = { _, _ -> error("boom") })
        )

        val result = repository.playlist("https://playlist")

        assertEquals(
            ProviderFailureReason.UNKNOWN,
            (result as ProviderResult.Failure).reason
        )
    }
}

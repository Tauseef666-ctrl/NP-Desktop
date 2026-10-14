/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.playlist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.newpipe.app.player.MediaItem
import net.newpipe.app.provider.MediaPlayback
import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.PlaylistNavigation
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentKind
import net.newpipe.app.provider.ProviderContentPage
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.provider.ProviderRegistry
import net.newpipe.app.provider.ProviderResult

private val PLAYLIST_URL = "https://example.com/playlist/NewPipe"

private val VIDEO = ProviderContentItem(
    id = "v1",
    title = "First video",
    url = "https://example.com/watch?v=1",
    uploader = "NewPipe",
    durationSeconds = 125L,
    kind = ProviderContentKind.VIDEO
)

private val LIVE = ProviderContentItem(
    id = "l1",
    title = "Live now",
    url = "https://example.com/watch?v=2",
    uploader = "NewPipe",
    kind = ProviderContentKind.LIVE
)

/**
 * Media provider stand-in that answers playlist requests through [onPlaylist] (so a test can serve
 * different pages per [URL]/token) and counts the calls so tests can assert retry/load-more really
 * re-query.
 */
private class FakePlaylistProvider(
    private val onPlaylist: suspend (url: String, pageToken: String?) -> ProviderResult<ProviderContentPage>
) : MediaProvider {
    override val id: String = "fake"
    override val name: String = "Fake"
    override val isAvailable: Boolean = true
    override val capabilities: ProviderCapabilities = ProviderCapabilities(playlists = true)

    var playlistCalls: Int = 0
        private set

    override suspend fun playlist(url: String, pageToken: String?): ProviderResult<ProviderContentPage> {
        playlistCalls++
        return onPlaylist(url, pageToken)
    }
}

/**
 * Playback stand-in that answers with a fixed [result] and records the last item it was asked to
 * play, so tests can assert the view model forwarded the tapped item.
 */
private class FakePlaylistPlayback(
    private val result: ProviderResult<Unit> = ProviderResult.Success(Unit)
) : MediaPlayback {
    var lastItem: ProviderContentItem? = null
        private set

    override suspend fun resolve(item: ProviderContentItem): ProviderResult<MediaItem> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED)

    override suspend fun play(item: ProviderContentItem): ProviderResult<Unit> {
        lastItem = item
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    private lateinit var registry: ProviderRegistry
    private lateinit var repository: ProviderContentRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        registry = ProviderRegistry()
        repository = ProviderContentRepository(registry).apply {
            // Required: an unconfined dispatcher drains the repository's dispatch hop inside the
            // test, so no coroutine leaks and resumes on Main in a later test.
            dispatcher = Dispatchers.Unconfined
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun register(provider: MediaProvider) = registry.register(provider)

    private fun viewModel(
        nav: PlaylistNavigation = PlaylistNavigation(),
        mediaPlayback: MediaPlayback = FakePlaylistPlayback()
    ) = PlaylistViewModel(repository, nav, mediaPlayback)

    @Test
    fun loadsSelectionOnInitPublishesSuccess() = runTest {
        val nav = PlaylistNavigation()
        nav.select(PLAYLIST_URL, "My Playlist")
        val page = ProviderContentPage(listOf(VIDEO, LIVE), nextPageToken = "PAGE2")
        val provider = FakePlaylistProvider { _, _ -> ProviderResult.Success(page) }
        register(provider)
        val vm = viewModel(nav)

        val state = vm.state.first { it is PlaylistState.Success }

        assertEquals(
            PlaylistState.Success(listOf(VIDEO, LIVE), nextPageToken = "PAGE2", loadingMore = false),
            state
        )
        assertEquals(1, provider.playlistCalls)
        assertEquals(PlaylistNavigation.Selection(PLAYLIST_URL, "My Playlist"), vm.selection.value)
    }

    @Test
    fun noSelectionStaysIdle() {
        val vm = viewModel()

        assertTrue(vm.state.value is PlaylistState.Idle)
        assertNull(vm.selection.value)
    }

    @Test
    fun providerFailurePublishesReason() = runTest {
        val nav = PlaylistNavigation()
        nav.select(PLAYLIST_URL, "My Playlist")
        val failure = ProviderResult.Failure(ProviderFailureReason.PARSE, "bad payload")
        register(FakePlaylistProvider { _, _ -> failure })
        val vm = viewModel(nav)

        val state = vm.state.first { it is PlaylistState.Failure }

        assertEquals(PlaylistState.Failure(ProviderFailureReason.PARSE), state)
    }

    @Test
    fun retryReQueriesProvider() = runTest {
        val nav = PlaylistNavigation()
        nav.select(PLAYLIST_URL, "My Playlist")
        val page = ProviderContentPage(listOf(VIDEO))
        val provider = FakePlaylistProvider { _, _ -> ProviderResult.Success(page) }
        register(provider)
        val vm = viewModel(nav)
        vm.state.first { it !is PlaylistState.Loading }
        assertEquals(1, provider.playlistCalls)

        vm.retry()

        val state = vm.state.first { it !is PlaylistState.Loading }
        assertTrue(state is PlaylistState.Success)
        assertEquals(2, provider.playlistCalls)
    }

    @Test
    fun loadMoreAppendsItemsAndUpdatesToken() = runTest {
        val nav = PlaylistNavigation()
        nav.select(PLAYLIST_URL, "My Playlist")
        val provider = FakePlaylistProvider { _, pageToken ->
            if (pageToken == null) {
                ProviderResult.Success(ProviderContentPage(listOf(VIDEO), nextPageToken = "PAGE2"))
            } else {
                ProviderResult.Success(ProviderContentPage(listOf(LIVE), nextPageToken = null))
            }
        }
        register(provider)
        val vm = viewModel(nav)
        vm.state.first { it !is PlaylistState.Loading }
        assertEquals(1, provider.playlistCalls)

        vm.loadMore()

        val state = vm.state.first {
            it is PlaylistState.Success && !it.loadingMore && it.items.contains(LIVE)
        } as PlaylistState.Success
        assertEquals(listOf(VIDEO, LIVE), state.items)
        assertNull(state.nextPageToken)
        assertEquals(2, provider.playlistCalls)
    }

    @Test
    fun loadMoreIsNoopWhenNextPageTokenIsNull() = runTest {
        val nav = PlaylistNavigation()
        nav.select(PLAYLIST_URL, "My Playlist")
        val page = ProviderContentPage(listOf(VIDEO), nextPageToken = null)
        val provider = FakePlaylistProvider { _, _ -> ProviderResult.Success(page) }
        register(provider)
        val vm = viewModel(nav)
        vm.state.first { it !is PlaylistState.Loading }
        assertEquals(1, provider.playlistCalls)

        vm.loadMore()

        assertEquals(1, provider.playlistCalls)
        val state = vm.state.value as PlaylistState.Success
        assertFalse(state.loadingMore)
        assertNull(state.nextPageToken)
        assertEquals(listOf(VIDEO), state.items)
    }

    @Test
    fun playOnSuccessPublishesLaunched() {
        val vm = viewModel()

        vm.play(VIDEO)

        assertEquals(ProviderPlaybackState.Launched, vm.playbackState.value)
    }

    @Test
    fun playOnFailurePublishesFailureWithReason() {
        val vm = viewModel(mediaPlayback = FakePlaylistPlayback(ProviderResult.Failure(ProviderFailureReason.NETWORK)))

        vm.play(VIDEO)

        assertEquals(
            ProviderPlaybackState.Failure(ProviderFailureReason.NETWORK),
            vm.playbackState.value
        )
    }

    @Test
    fun acknowledgePlaybackResetsToIdle() {
        val vm = viewModel()
        vm.play(VIDEO)
        assertEquals(ProviderPlaybackState.Launched, vm.playbackState.value)

        vm.acknowledgePlayback()

        assertEquals(ProviderPlaybackState.Idle, vm.playbackState.value)
    }

    @Test
    fun playAllOnSuccessPublishesLaunched() = runTest {
        val nav = PlaylistNavigation()
        nav.select(PLAYLIST_URL, "My Playlist")
        val page = ProviderContentPage(listOf(VIDEO, LIVE), nextPageToken = null)
        register(FakePlaylistProvider { _, _ -> ProviderResult.Success(page) })
        val vm = viewModel(nav)
        vm.state.first { it is PlaylistState.Success }

        vm.playAll()

        assertEquals(ProviderPlaybackState.Launched, vm.playbackState.value)
    }

    @Test
    fun playAllNoopWhenIdle() {
        val vm = viewModel()

        vm.playAll()

        assertEquals(ProviderPlaybackState.Idle, vm.playbackState.value)
    }
}
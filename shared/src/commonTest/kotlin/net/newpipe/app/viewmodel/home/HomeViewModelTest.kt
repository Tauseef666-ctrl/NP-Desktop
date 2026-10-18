/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.home

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.newpipe.app.download.DownloadEngine
import net.newpipe.app.download.DownloadRequest
import net.newpipe.app.download.DownloadState
import net.newpipe.app.download.DownloadTask
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlayQueueStore
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.PlaybackState
import net.newpipe.app.player.PlaybackStatus
import net.newpipe.app.player.PlayerEngine
import net.newpipe.app.player.PlaylistStore
import net.newpipe.app.player.Quality
import net.newpipe.app.player.RecentlyPlayedStore
import net.newpipe.app.player.SavedPlaylist
import net.newpipe.app.provider.MediaPlayback
import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentPage
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.provider.ProviderRegistry
import net.newpipe.app.provider.ProviderResult
import net.newpipe.app.screen.home.HomeRailItem
import net.newpipe.app.screen.home.HomeRailType
import net.newpipe.app.viewmodel.home.HomeTrendingState

private class FakePlayerEngine : PlayerEngine {
    override val isAvailable: Boolean = true

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    val prepared = mutableListOf<MediaItem>()

    override suspend fun prepare(item: MediaItem) {
        prepared += item
        _state.value = _state.value.copy(mediaId = item.id, status = PlaybackStatus.READY)
    }

    override fun play() {
        _state.value = _state.value.copy(isPlaying = true)
    }

    override fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun stop() = Unit

    override fun seekTo(positionMs: Long) {
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    override fun setSpeed(speed: Float) = Unit
    override fun setVolume(volume: Float) = Unit

    override fun setMuted(muted: Boolean) {
        _state.value = _state.value.copy(isMuted = muted)
    }

    override fun setQuality(quality: Quality) = Unit
    override fun release() = Unit
}

private class FakeDownloadEngine(
    override val isAvailable: Boolean = true
) : DownloadEngine {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    override val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun setTasks(list: List<DownloadTask>) {
        _tasks.value = list
    }

    override fun enqueue(request: DownloadRequest): DownloadTask {
        val task = DownloadTask(request)
        _tasks.value = _tasks.value + task
        return task
    }

    override fun start(id: String) = Unit
    override fun pause(id: String) = Unit
    override fun cancel(id: String) = Unit
    override fun remove(id: String) = Unit
    override fun clearCompleted() = Unit
}

private class FakeHomeProvider(
    private val onTrending: suspend () -> ProviderResult<ProviderContentPage>
) : MediaProvider {
    override val id: String = "fake"
    override val name: String = "Fake provider"
    override val isAvailable: Boolean = true
    override val capabilities: ProviderCapabilities = ProviderCapabilities(trending = true)

    override suspend fun trending(pageToken: String?): ProviderResult<ProviderContentPage> = onTrending()
}

private class FakeHomePlayback(
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
class HomeViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Harness(
        val viewModel: HomeViewModel,
        val playerEngine: FakePlayerEngine,
        val settings: MapSettings
    )

    private fun harness(
        settings: MapSettings = MapSettings(),
        downloadEngine: FakeDownloadEngine = FakeDownloadEngine(),
        registry: ProviderRegistry = ProviderRegistry(),
        mediaPlayback: MediaPlayback = FakeHomePlayback()
    ): Harness {
        val playerEngine = FakePlayerEngine()
        val playback = PlaybackController(
            playerEngine,
            PlaybackResumeStore(settings),
            PlayQueueStore(settings, Json),
            settings
        )
        val viewModel = HomeViewModel(
            RecentlyPlayedStore(settings, Json),
            PlaybackResumeStore(settings),
            downloadEngine,
            playback,
            ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined },
            mediaPlayback
        )
        return Harness(viewModel, playerEngine, settings)
    }

    private fun completedTask(id: String, title: String = ""): DownloadTask =
        DownloadTask(
            request = DownloadRequest(id = id, url = "https://example.org/$id", destinationPath = "/tmp/$id", title = title),
            status = DownloadState.COMPLETED,
            filePath = "/tmp/$id"
        )

    @Test
    fun isEmptyIsHonestWhenAllSourcesEmpty() {
        val harness = harness()
        assertTrue(harness.viewModel.isEmpty.value)
        assertTrue(harness.viewModel.sections.value.isEmpty())
    }

    @Test
    fun sectionsEmitAllPopulatedSourcesInCanonicalOrder() {
        val settings = MapSettings()
        PlaybackResumeStore(settings).save("r1", 12_000L, 90_000L, title = "Resume", url = "file:///r1")
        RecentlyPlayedStore(settings, Json).record(
            MediaItem(id = "p1", title = "Played", url = "file:///p1")
        )
        val engine = FakeDownloadEngine()
        engine.setTasks(listOf(completedTask("c1", title = "Done")))
        PlaylistStore(settings, Json).save(
            SavedPlaylist(
                id = "pl",
                name = "Mix",
                items = listOf(MediaItem(id = "t1", title = "Track", url = "file:///t1"))
            )
        )

        val harness = harness(settings = settings, downloadEngine = engine)

        assertEquals(
            listOf(
                HomeRailType.CONTINUE_WATCHING,
                HomeRailType.RECENTLY_PLAYED,
                HomeRailType.DOWNLOADS,
                HomeRailType.PLAYLISTS
            ),
            harness.viewModel.sections.value.map { it.type }
        )
        assertFalse(harness.viewModel.isEmpty.value)
    }

    @Test
    fun refreshRereadsResumeEntriesFromTheNonObservableStore() {
        val settings = MapSettings()
        val harness = harness(settings = settings)
        assertTrue(harness.viewModel.sections.value.isEmpty())

        PlaybackResumeStore(settings).save("r2", 20_000L, 100_000L, title = "Now", url = "file:///r2")
        harness.viewModel.refresh()

        assertEquals(
            listOf(HomeRailType.CONTINUE_WATCHING),
            harness.viewModel.sections.value.map { it.type }
        )
    }

    @Test
    fun playStartsPlaybackForItemWithUrl() = runTest {
        val harness = harness()
        harness.viewModel.play(
            HomeRailItem(id = "a", title = "Alpha", subtitle = null, url = "file:///a")
        )

        assertEquals("a", harness.playerEngine.prepared.last().id)
        assertEquals("file:///a", harness.playerEngine.prepared.last().url)
        assertEquals("Alpha", harness.playerEngine.prepared.last().title)
    }

    @Test
    fun playWithBlankUrlIsANoOp() = runTest {
        val harness = harness()
        harness.viewModel.play(
            HomeRailItem(id = "x", title = "X", subtitle = null, url = "")
        )

        assertTrue(harness.playerEngine.prepared.isEmpty())
    }

    @Test
    fun trendingWithoutAProviderFailsHonestly() {
        val harness = harness()

        assertEquals(
            HomeTrendingState.Failure(ProviderFailureReason.NO_PROVIDER),
            harness.viewModel.trending.value
        )
    }

    @Test
    fun trendingSuccessPublishesProviderItems() {
        val page = ProviderContentPage(
            items = listOf(
                ProviderContentItem(id = "v1", title = "One", url = "https://example.org/v1"),
                ProviderContentItem(id = "v2", title = "Two", url = "https://example.org/v2")
            )
        )
        val registry = ProviderRegistry().apply {
            register(FakeHomeProvider { ProviderResult.Success(page) })
        }

        val harness = harness(registry = registry)

        assertEquals(HomeTrendingState.Items(page.items), harness.viewModel.trending.value)
    }

    @Test
    fun playTrendingHandsTheItemToThePlaybackSession() = runTest {
        val playback = FakeHomePlayback()
        val harness = harness(mediaPlayback = playback)
        val item = ProviderContentItem(id = "v9", title = "Nine", url = "https://example.org/v9")

        harness.viewModel.playTrending(item)

        assertEquals(item, playback.lastItem)
        assertEquals(ProviderPlaybackState.Launched, harness.viewModel.playbackState.value)
    }

    @Test
    fun retryTrendingRecoversAfterAFailure() {
        var shouldFail = true
        val registry = ProviderRegistry().apply {
            register(FakeHomeProvider {
                if (shouldFail) {
                    ProviderResult.Failure(ProviderFailureReason.NETWORK)
                } else {
                    ProviderResult.Success(ProviderContentPage(listOf(itemFor("ok"))))
                }
            })
        }
        val harness = harness(registry = registry)
        assertEquals(
            HomeTrendingState.Failure(ProviderFailureReason.NETWORK),
            harness.viewModel.trending.value
        )

        shouldFail = false
        harness.viewModel.retryTrending()

        assertEquals(
            HomeTrendingState.Items(listOf(itemFor("ok"))),
            harness.viewModel.trending.value
        )
    }

    private fun itemFor(id: String) =
        ProviderContentItem(id = id, title = "Item $id", url = "https://example.org/$id")
}
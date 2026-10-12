/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.PlaybackState
import net.newpipe.app.player.PlaybackStatus
import net.newpipe.app.player.PlayQueueStore
import net.newpipe.app.player.PlayerEngine
import net.newpipe.app.player.Quality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class StubPlaybackEngine : PlayerEngine {
    override val isAvailable: Boolean = true

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    val prepared = mutableListOf<MediaItem>()

    override suspend fun prepare(item: MediaItem) {
        prepared += item
        _state.value = _state.value.copy(mediaId = item.id, status = PlaybackStatus.READY)
    }

    override fun play() = Unit
    override fun pause() = Unit
    override fun stop() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun setSpeed(speed: Float) = Unit
    override fun setVolume(volume: Float) = Unit
    override fun setMuted(muted: Boolean) = Unit
    override fun setQuality(quality: Quality) = Unit
    override fun release() = Unit
}

private class StubResolveProvider(
    override val id: String = "stub",
    override val name: String = "Stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(resolve = true),
    private val result: ProviderResult<ResolvedMedia> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED)
) : MediaProvider {
    override suspend fun resolve(url: String): ProviderResult<ResolvedMedia> = result
}

class ProviderPlaybackTest {

    private fun controller(
        engine: StubPlaybackEngine,
        settings: Settings = MapSettings()
    ): PlaybackController =
        PlaybackController(engine, PlaybackResumeStore(settings), PlayQueueStore(settings, Json), settings)

    private fun playbackWith(provider: MediaProvider, engine: StubPlaybackEngine): ProviderPlayback {
        val registry = ProviderRegistry().apply { register(provider) }
        val repository = ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined }
        return ProviderPlayback(repository, controller(engine))
    }

    private fun item(
        id: String = "id",
        title: String = "Title",
        url: String = "https://page",
        thumbnailUrl: String? = "https://thumb",
        durationSeconds: Long? = 60,
        kind: ProviderContentKind = ProviderContentKind.VIDEO
    ) = ProviderContentItem(
        id = id,
        title = title,
        url = url,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        kind = kind
    )

    @Test
    fun resolveMapsSuccessToMediaItem() = runBlocking {
        val engine = StubPlaybackEngine()
        val provider = StubResolveProvider(
            result = ProviderResult.Success(
                ResolvedMedia(
                    streamUrl = "https://s",
                    isLive = false,
                    audioOnly = false,
                    durationSeconds = 90
                )
            )
        )
        val playback = playbackWith(provider, engine)

        val result = playback.resolve(item())

        val media = (result as ProviderResult.Success).value
        assertEquals("https://s", media.url)
        assertEquals(90_000L, media.durationMs)
        assertEquals("id", media.id)
        assertEquals("Title", media.title)
        assertEquals("https://thumb", media.thumbnailUrl)
    }

    @Test
    fun resolveFallsBackToItemDurationWhenStreamDoesNotReportOne() = runBlocking {
        val engine = StubPlaybackEngine()
        val provider = StubResolveProvider(
            result = ProviderResult.Success(ResolvedMedia(streamUrl = "https://s", durationSeconds = null))
        )
        val playback = playbackWith(provider, engine)

        val result = playback.resolve(item(durationSeconds = 60))

        val media = (result as ProviderResult.Success).value
        assertEquals(60_000L, media.durationMs)
    }

    @Test
    fun resolveMarksLiveItemFromKindEvenWhenStreamNotFlagged() = runBlocking {
        val engine = StubPlaybackEngine()
        val provider = StubResolveProvider(
            result = ProviderResult.Success(ResolvedMedia(streamUrl = "https://s", isLive = false))
        )
        val playback = playbackWith(provider, engine)

        val result = playback.resolve(item(kind = ProviderContentKind.LIVE))

        val media = (result as ProviderResult.Success).value
        assertTrue(media.isLive)
    }

    @Test
    fun resolvePropagatesFailureReason() = runBlocking {
        val engine = StubPlaybackEngine()
        val provider = StubResolveProvider(
            result = ProviderResult.Failure(ProviderFailureReason.PARSE)
        )
        val playback = playbackWith(provider, engine)

        assertEquals(
            ProviderResult.Failure(ProviderFailureReason.PARSE),
            playback.resolve(item())
        )
    }

    @Test
    fun playEnqueuesIntoControllerAndReturnsSuccess() = runBlocking {
        val engine = StubPlaybackEngine()
        val provider = StubResolveProvider(
            result = ProviderResult.Success(ResolvedMedia(streamUrl = "https://stream"))
        )
        val registry = ProviderRegistry().apply { register(provider) }
        val repository = ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined }
        val controller = controller(engine)
        val playback = ProviderPlayback(repository, controller)

        val result = playback.play(item())

        assertEquals(ProviderResult.Success(Unit), result)
        assertEquals("https://stream", controller.queueState.value.current?.url)
        assertEquals(listOf("https://stream"), engine.prepared.map { it.url })
    }

    @Test
    fun playDoesNotTouchEngineOnFailure() = runBlocking {
        val engine = StubPlaybackEngine()
        val provider = StubResolveProvider(
            result = ProviderResult.Failure(ProviderFailureReason.NETWORK)
        )
        val playback = playbackWith(provider, engine)

        val result = playback.play(item())

        assertEquals(ProviderResult.Failure(ProviderFailureReason.NETWORK), result)
        assertTrue(engine.prepared.isEmpty())
    }
}

/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.captions

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
import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderRegistry
import net.newpipe.app.provider.ProviderResult
import net.newpipe.app.provider.SubtitleTrack

private const val URL_A = "https://example.com/watch?v=A"
private const val URL_B = "https://example.com/watch?v=B"

/**
 * Media provider stand-in that answers [subtitles] with a lambda and records the last URL and call
 * count so tests can assert caching and reload behaviour.
 */
private class FakeCaptionsProvider(
    private val onSubtitles: suspend (String) -> ProviderResult<List<SubtitleTrack>>
) : MediaProvider {
    override val id: String = "fake"
    override val name: String = "Fake"
    override val isAvailable: Boolean = true
    override val capabilities: ProviderCapabilities = ProviderCapabilities(resolve = true)

    var lastUrl: String? = null
        private set

    var calls: Int = 0
        private set

    override suspend fun subtitles(url: String): ProviderResult<List<SubtitleTrack>> {
        calls++
        lastUrl = url
        return onSubtitles(url)
    }
}

/**
 * Media provider stand-in that advertises resolve but keeps the default [subtitles] failure, so the
 * repository reports the operation as unsupported.
 */
private class ResolveOnlyProvider : MediaProvider {
    override val id: String = "fake-resolve"
    override val name: String = "Fake resolve"
    override val isAvailable: Boolean = true
    override val capabilities: ProviderCapabilities = ProviderCapabilities(resolve = true)
}

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(
        provider: MediaProvider? = null
    ): ProviderContentRepository {
        val registry = ProviderRegistry()
        if (provider != null) registry.register(provider)
        return ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined }
    }

    @Test
    fun loadNullStaysIdle() {
        val viewModel = CaptionsViewModel(repository(FakeCaptionsProvider {
            ProviderResult.Success(emptyList())
        }))

        viewModel.load(null)

        assertEquals(CaptionsState.Idle, viewModel.state.value)
    }

    @Test
    fun loadSuccessPublishesTracks() = runTest {
        val tracks = listOf(
            SubtitleTrack(
                id = "en|manual|vtt",
                languageTag = "en",
                displayName = "English",
                format = "vtt"
            )
        )
        val provider = FakeCaptionsProvider { ProviderResult.Success(tracks) }
        val viewModel = CaptionsViewModel(repository(provider))

        viewModel.load(URL_A)

        val state = viewModel.state.first { it !is CaptionsState.Loading }
        assertEquals(CaptionsState.Success(tracks), state)
        assertEquals(1, provider.calls)
        assertEquals(URL_A, provider.lastUrl)
    }

    @Test
    fun loadEmptyTracksIsSuccess() = runTest {
        val provider = FakeCaptionsProvider { ProviderResult.Success(emptyList()) }
        val viewModel = CaptionsViewModel(repository(provider))

        viewModel.load(URL_A)

        val state = viewModel.state.first { it !is CaptionsState.Loading }
        assertEquals(CaptionsState.Success(emptyList()), state)
    }

    @Test
    fun loadFailurePublishesUnavailable() = runTest {
        val provider = FakeCaptionsProvider {
            ProviderResult.Failure(ProviderFailureReason.PARSE)
        }
        val viewModel = CaptionsViewModel(repository(provider))

        viewModel.load(URL_A)

        val state = viewModel.state.first { it !is CaptionsState.Loading }
        assertEquals(CaptionsState.Unavailable(ProviderFailureReason.PARSE), state)
    }

    @Test
    fun loadSameIdTwiceLoadsOnce() = runTest {
        val tracks = listOf(
            SubtitleTrack(id = "en", languageTag = "en", displayName = "English")
        )
        val provider = FakeCaptionsProvider { ProviderResult.Success(tracks) }
        val viewModel = CaptionsViewModel(repository(provider))
        viewModel.load(URL_A)
        viewModel.state.first { it !is CaptionsState.Loading }
        assertEquals(1, provider.calls)

        viewModel.load(URL_A)

        assertEquals(1, provider.calls)
    }

    @Test
    fun loadNewIdReloads() = runTest {
        val provider = FakeCaptionsProvider { url ->
            ProviderResult.Success(
                listOf(SubtitleTrack(id = url, languageTag = "en", displayName = url))
            )
        }
        val viewModel = CaptionsViewModel(repository(provider))
        viewModel.load(URL_A)
        viewModel.state.first { it !is CaptionsState.Loading }

        viewModel.load(URL_B)

        val state = viewModel.state.first { it !is CaptionsState.Loading }
        assertEquals(
            CaptionsState.Success(
                listOf(SubtitleTrack(id = URL_B, languageTag = "en", displayName = URL_B))
            ),
            state
        )
        assertEquals(2, provider.calls)
        assertEquals(URL_B, provider.lastUrl)
    }

    @Test
    fun unsupportedProviderPublishesUnsupported() = runTest {
        val viewModel = CaptionsViewModel(repository(ResolveOnlyProvider()))

        viewModel.load(URL_A)

        val state = viewModel.state.first { it !is CaptionsState.Loading }
        assertEquals(CaptionsState.Unavailable(ProviderFailureReason.UNSUPPORTED), state)
    }

    @Test
    fun noProviderPublishesNoProvider() = runTest {
        val viewModel = CaptionsViewModel(repository(provider = null))

        viewModel.load(URL_A)

        val state = viewModel.state.first { it !is CaptionsState.Loading }
        assertEquals(CaptionsState.Unavailable(ProviderFailureReason.NO_PROVIDER), state)
    }
}

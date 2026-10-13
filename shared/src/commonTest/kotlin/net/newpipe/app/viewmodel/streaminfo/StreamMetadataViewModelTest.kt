/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.streaminfo

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
import net.newpipe.app.provider.StreamMetadata

private const val URL_A = "https://example.com/watch?v=A"
private const val URL_B = "https://example.com/watch?v=B"

/**
 * Media provider stand-in that answers [streamMetadata] with a lambda and records the last URL and
 * call count so tests can assert caching and reload behaviour.
 */
private class FakeMetadataProvider(
    private val onStreamMetadata: suspend (String) -> ProviderResult<StreamMetadata>
) : MediaProvider {
    override val id: String = "fake"
    override val name: String = "Fake"
    override val isAvailable: Boolean = true
    override val capabilities: ProviderCapabilities = ProviderCapabilities(resolve = true)

    var lastUrl: String? = null
        private set

    var calls: Int = 0
        private set

    override suspend fun streamMetadata(url: String): ProviderResult<StreamMetadata> {
        calls++
        lastUrl = url
        return onStreamMetadata(url)
    }
}

/**
 * Media provider stand-in that advertises resolve but keeps the default [streamMetadata] failure, so
 * the repository reports the operation as unsupported.
 */
private class ResolveOnlyProvider : MediaProvider {
    override val id: String = "fake-resolve"
    override val name: String = "Fake resolve"
    override val isAvailable: Boolean = true
    override val capabilities: ProviderCapabilities = ProviderCapabilities(resolve = true)
}

@OptIn(ExperimentalCoroutinesApi::class)
class StreamMetadataViewModelTest {

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
        val viewModel = StreamMetadataViewModel(repository(FakeMetadataProvider {
            ProviderResult.Success(StreamMetadata(title = "unused"))
        }))

        viewModel.load(null)

        assertEquals(StreamMetadataState.Idle, viewModel.state.value)
    }

    @Test
    fun loadSuccessPublishesMetadata() = runTest {
        val metadata = StreamMetadata(title = "T", viewCount = 7L)
        val provider = FakeMetadataProvider { ProviderResult.Success(metadata) }
        val viewModel = StreamMetadataViewModel(repository(provider))

        viewModel.load(URL_A)

        val state = viewModel.state.first { it !is StreamMetadataState.Loading }
        assertEquals(StreamMetadataState.Success(metadata), state)
        assertEquals(1, provider.calls)
        assertEquals(URL_A, provider.lastUrl)
    }

    @Test
    fun loadFailurePublishesUnavailable() = runTest {
        val provider = FakeMetadataProvider {
            ProviderResult.Failure(ProviderFailureReason.PARSE)
        }
        val viewModel = StreamMetadataViewModel(repository(provider))

        viewModel.load(URL_A)

        val state = viewModel.state.first { it !is StreamMetadataState.Loading }
        assertEquals(StreamMetadataState.Unavailable(ProviderFailureReason.PARSE), state)
    }

    @Test
    fun loadSameIdTwiceLoadsOnce() = runTest {
        val metadata = StreamMetadata(title = "A")
        val provider = FakeMetadataProvider { ProviderResult.Success(metadata) }
        val viewModel = StreamMetadataViewModel(repository(provider))
        viewModel.load(URL_A)
        viewModel.state.first { it !is StreamMetadataState.Loading }
        assertEquals(1, provider.calls)

        viewModel.load(URL_A)

        assertEquals(1, provider.calls)
    }

    @Test
    fun loadNewIdReloads() = runTest {
        val provider = FakeMetadataProvider { url ->
            ProviderResult.Success(StreamMetadata(title = url))
        }
        val viewModel = StreamMetadataViewModel(repository(provider))
        viewModel.load(URL_A)
        viewModel.state.first { it !is StreamMetadataState.Loading }

        viewModel.load(URL_B)

        val state = viewModel.state.first { it !is StreamMetadataState.Loading }
        assertEquals(StreamMetadataState.Success(StreamMetadata(title = URL_B)), state)
        assertEquals(2, provider.calls)
        assertEquals(URL_B, provider.lastUrl)
    }

    @Test
    fun unsupportedProviderPublishesUnsupported() = runTest {
        val viewModel = StreamMetadataViewModel(repository(ResolveOnlyProvider()))

        viewModel.load(URL_A)

        val state = viewModel.state.first { it !is StreamMetadataState.Loading }
        assertEquals(StreamMetadataState.Unavailable(ProviderFailureReason.UNSUPPORTED), state)
    }

    @Test
    fun noProviderPublishesNoProvider() = runTest {
        val viewModel = StreamMetadataViewModel(repository(provider = null))

        viewModel.load(URL_A)

        val state = viewModel.state.first { it !is StreamMetadataState.Loading }
        assertEquals(StreamMetadataState.Unavailable(ProviderFailureReason.NO_PROVIDER), state)
    }
}

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

private class SubtitlesStubProvider(
    override val id: String = "subtitles-stub",
    override val name: String = "Subtitles stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        resolve = true
    ),
    private val onSubtitles: suspend (url: String) -> ProviderResult<List<SubtitleTrack>> =
        { _ -> ProviderResult.Success(emptyList()) }
) : MediaProvider {

    var lastUrl: String? = null
        private set

    var subtitlesCalled: Boolean = false
        private set

    override suspend fun subtitles(url: String): ProviderResult<List<SubtitleTrack>> {
        lastUrl = url
        subtitlesCalled = true
        return onSubtitles(url)
    }
}

private class SubtitlesUnsupportedStubProvider(
    override val id: String = "subtitles-unsupported-stub",
    override val name: String = "Subtitles unsupported stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        resolve = true
    )
) : MediaProvider

class ProviderContentRepositorySubtitlesTest {

    private fun repository(vararg providers: MediaProvider): ProviderContentRepository {
        val registry = ProviderRegistry()
        providers.forEach { registry.register(it) }
        return ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined }
    }

    @Test
    fun subtitlesForwardsUrlAndReturnsTracks() = runTest {
        val tracks = listOf(
            SubtitleTrack(
                id = "en|manual|vtt",
                languageTag = "en",
                displayName = "English",
                format = "vtt"
            )
        )
        val provider = SubtitlesStubProvider(
            onSubtitles = { _ -> ProviderResult.Success(tracks) }
        )
        val repository = repository(provider)

        val result = repository.subtitles("https://video")

        assertTrue(provider.subtitlesCalled)
        assertEquals("https://video", provider.lastUrl)
        assertEquals(tracks, (result as ProviderResult.Success<List<SubtitleTrack>>).value)
    }

    @Test
    fun subtitlesEmptyListIsSuccess() = runTest {
        val provider = SubtitlesStubProvider(
            onSubtitles = { _ -> ProviderResult.Success(emptyList()) }
        )
        val repository = repository(provider)

        val result = repository.subtitles("https://video")

        assertTrue(provider.subtitlesCalled)
        assertEquals(emptyList(), (result as ProviderResult.Success<List<SubtitleTrack>>).value)
    }

    @Test
    fun subtitlesWithoutProviderFailsNoProvider() = runTest {
        val repository = repository()

        val result = repository.subtitles("https://video")

        assertEquals(
            ProviderFailureReason.NO_PROVIDER,
            (result as ProviderResult.Failure).reason
        )
    }

    @Test
    fun subtitlesUnsupportedByProviderFailsUnsupported() = runTest {
        val provider = SubtitlesUnsupportedStubProvider()
        val repository = repository(provider)

        val result = repository.subtitles("https://video")

        assertEquals(
            ProviderFailureReason.UNSUPPORTED,
            (result as ProviderResult.Failure).reason
        )
    }
}

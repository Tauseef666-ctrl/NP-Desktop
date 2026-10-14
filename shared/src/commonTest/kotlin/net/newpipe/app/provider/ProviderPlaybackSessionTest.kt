/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.newpipe.app.player.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals

private class ConfigurableMediaPlayback(
    private val result: ProviderResult<Unit> = ProviderResult.Success(Unit),
    private val gate: CompletableDeferred<Unit>? = null
) : MediaPlayback {

    var playCalls: Int = 0
        private set

    override suspend fun resolve(item: ProviderContentItem): ProviderResult<MediaItem> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED)

    override suspend fun play(item: ProviderContentItem): ProviderResult<Unit> {
        playCalls++
        gate?.await()
        return result
    }
}

class ProviderPlaybackSessionTest {

    private fun item(id: String = "id") = ProviderContentItem(
        id = id,
        title = id,
        url = "https://page/$id"
    )

    @Test
    fun initialStateIsIdle() = runTest {
        val session = ProviderPlaybackSession(ConfigurableMediaPlayback(), this)

        assertEquals(ProviderPlaybackState.Idle, session.state.value)
    }

    @Test
    fun playPublishesLaunchedOnSuccess() = runTest {
        val fake = ConfigurableMediaPlayback(result = ProviderResult.Success(Unit))
        val session = ProviderPlaybackSession(fake, this)

        session.play(item())
        advanceUntilIdle()

        assertEquals(ProviderPlaybackState.Launched, session.state.value)
    }

    @Test
    fun playPublishesLoadingWhileInFlight() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fake = ConfigurableMediaPlayback(gate = gate)
        val session = ProviderPlaybackSession(fake, this)

        session.play(item())
        assertEquals(ProviderPlaybackState.Loading, session.state.value)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(ProviderPlaybackState.Launched, session.state.value)
    }

    @Test
    fun playIsIgnoredWhileAlreadyLoading() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fake = ConfigurableMediaPlayback(gate = gate)
        val session = ProviderPlaybackSession(fake, this)

        session.play(item())
        advanceUntilIdle()
        assertEquals(ProviderPlaybackState.Loading, session.state.value)

        session.play(item())

        assertEquals(1, fake.playCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(ProviderPlaybackState.Launched, session.state.value)
        assertEquals(1, fake.playCalls)
    }

    @Test
    fun playPublishesFailureWithReason() = runTest {
        val fake = ConfigurableMediaPlayback(
            result = ProviderResult.Failure(ProviderFailureReason.NETWORK)
        )
        val session = ProviderPlaybackSession(fake, this)

        session.play(item())
        advanceUntilIdle()

        assertEquals(
            ProviderPlaybackState.Failure(ProviderFailureReason.NETWORK),
            session.state.value
        )
    }

    @Test
    fun playAllDoesNothingIfEmpty() = runTest {
        val fake = ConfigurableMediaPlayback(result = ProviderResult.Success(Unit))
        val session = ProviderPlaybackSession(fake, this)

        session.playAll(emptyList())
        advanceUntilIdle()

        assertEquals(ProviderPlaybackState.Idle, session.state.value)
        assertEquals(0, fake.playCalls)
    }

    @Test
    fun playAllPlaysFirstUntilSuccessAndBreaks() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fake = ConfigurableMediaPlayback(result = ProviderResult.Success(Unit))
        val session = ProviderPlaybackSession(fake, this)

        session.playAll(listOf(item("a"), item("b")))
        advanceUntilIdle()

        assertEquals(ProviderPlaybackState.Launched, session.state.value)
        assertEquals(1, fake.playCalls)
    }

    @Test
    fun playAllReturnsUnsupportedIfAllFail() = runTest {
        val fake = ConfigurableMediaPlayback(
            result = ProviderResult.Failure(ProviderFailureReason.NETWORK)
        )
        val session = ProviderPlaybackSession(fake, this)

        session.playAll(listOf(item("a"), item("b")))
        advanceUntilIdle()

        assertEquals(
            ProviderPlaybackState.Failure(ProviderFailureReason.UNSUPPORTED),
            session.state.value
        )
    }

    @Test
    fun playAllIsIgnoredWhileLoading() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fake = ConfigurableMediaPlayback(gate = gate)
        val session = ProviderPlaybackSession(fake, this)

        session.play(item())
        advanceUntilIdle()
        assertEquals(ProviderPlaybackState.Loading, session.state.value)

        session.playAll(listOf(item("a"), item("b")))
        assertEquals(1, fake.playCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(ProviderPlaybackState.Launched, session.state.value)
    }
}

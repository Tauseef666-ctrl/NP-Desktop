/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.downloads

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import net.newpipe.app.download.DownloadEngine
import net.newpipe.app.download.DownloadFormat
import net.newpipe.app.download.DownloadRequest
import net.newpipe.app.download.DownloadStore
import net.newpipe.app.download.DownloadTask
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingDownloadEngine : DownloadEngine {
    override val isAvailable: Boolean = true

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    override val tasks: StateFlow<List<DownloadTask>> = _tasks

    val startedIds = mutableListOf<String>()

    override fun enqueue(request: DownloadRequest): DownloadTask {
        _tasks.value.firstOrNull { it.id == request.id }?.let { return it }
        val task = DownloadTask(request = request)
        _tasks.value = _tasks.value + task
        return task
    }

    override fun start(id: String) {
        startedIds += id
    }

    override fun pause(id: String) = Unit
    override fun cancel(id: String) = Unit
    override fun remove(id: String) = Unit
    override fun clearCompleted() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        engine: DownloadEngine = RecordingDownloadEngine()
    ) = DownloadsViewModel(
        engine = engine,
        store = DownloadStore(MapSettings(), Json),
        settings = MapSettings()
    )

    @Test
    fun addFromUrlWithoutSubtitleEnqueuesOnlyMainMission() {
        val engine = RecordingDownloadEngine()
        val viewModel = viewModel(engine)

        val added = viewModel.addFromUrl(
            url = "https://example.org/video.mp4",
            folder = "/downloads",
            format = DownloadFormat.HIGH
        )

        assertTrue(added)
        assertEquals(1, engine.tasks.value.size)
        assertNull(engine.tasks.value.single().request.subtitleUrl)
        assertEquals(1, engine.startedIds.size)
    }

    @Test
    fun addFromUrlWithSubtitleEnqueuesAndStartsCompanion() {
        val engine = RecordingDownloadEngine()
        val viewModel = viewModel(engine)

        val added = viewModel.addFromUrl(
            url = "https://example.org/video.mp4",
            folder = "/downloads",
            format = DownloadFormat.ORIGINAL,
            subtitleUrl = "https://example.org/video.en.vtt",
            subtitleLanguageTag = "en"
        )

        assertTrue(added)
        assertEquals(2, engine.tasks.value.size)
        val main = engine.tasks.value.first { it.id.endsWith("-sub").not() }
        val companion = engine.tasks.value.first { it.id.endsWith("-sub") }
        assertEquals("https://example.org/video.en.vtt", companion.request.url)
        assertEquals("/downloads/video.en.vtt", companion.request.destinationPath)
        assertNull(companion.request.subtitleUrl)
        assertEquals(main.id + "-sub", companion.id)
        assertTrue(engine.startedIds.contains(main.id))
        assertTrue(engine.startedIds.contains(companion.id))
    }

    @Test
    fun blankSubtitleUrlIsTreatedAsNoSubtitle() {
        val engine = RecordingDownloadEngine()
        val viewModel = viewModel(engine)

        viewModel.addFromUrl(
            url = "https://example.org/video.mp4",
            folder = "/downloads",
            format = DownloadFormat.ORIGINAL,
            subtitleUrl = "   ",
            subtitleLanguageTag = "en"
        )

        assertEquals(1, engine.tasks.value.size)
    }
}

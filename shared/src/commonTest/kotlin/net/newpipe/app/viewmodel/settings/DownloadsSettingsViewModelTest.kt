/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.download.DownloadEngine
import net.newpipe.app.download.DownloadRequest
import net.newpipe.app.download.DownloadTask
import net.newpipe.app.platform.defaultDownloadDirectory
import net.newpipe.app.preferences.DownloadsPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeDownloadEngine(
    override val isAvailable: Boolean = true
) : DownloadEngine {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    override val tasks: StateFlow<List<DownloadTask>> = _tasks

    var clearCompletedCalls: Int = 0
        private set

    override fun enqueue(request: DownloadRequest): DownloadTask {
        val task = DownloadTask(request)
        _tasks.value = _tasks.value + task
        return task
    }

    override fun start(id: String) = Unit
    override fun pause(id: String) = Unit
    override fun cancel(id: String) = Unit
    override fun remove(id: String) = Unit

    override fun clearCompleted() {
        clearCompletedCalls++
    }
}

class DownloadsSettingsViewModelTest {

    private fun viewModel(
        settings: Settings = MapSettings(),
        engine: DownloadEngine = FakeDownloadEngine()
    ) = DownloadsSettingsViewModel(settings, engine)

    @Test
    fun defaultWhenNoOverrideIsPlatformDefault() {
        val viewModel = viewModel()
        assertEquals(defaultDownloadDirectory(), viewModel.platformDefault)
        assertEquals(defaultDownloadDirectory(), viewModel.directory.value)
    }

    @Test
    fun setDirectoryPersistsAcrossViewModels() {
        val settings = MapSettings()
        val viewModel = viewModel(settings)

        viewModel.setDirectory("  /tmp/newpipe-movies  ")

        assertEquals("/tmp/newpipe-movies", viewModel.directory.value)
        assertEquals(
            "/tmp/newpipe-movies",
            settings.getString(DownloadsPreferences.KEY_DOWNLOAD_DIRECTORY, "")
        )
        assertEquals("/tmp/newpipe-movies", viewModel(settings).directory.value)
    }

    @Test
    fun resetDirectoryClearsOverride() {
        val settings = MapSettings()
        val viewModel = viewModel(settings)
        viewModel.setDirectory("/tmp/newpipe-movies")

        viewModel.resetDirectory()

        assertEquals("", settings.getString(DownloadsPreferences.KEY_DOWNLOAD_DIRECTORY, ""))
        assertEquals(defaultDownloadDirectory(), viewModel.directory.value)
    }

    @Test
    fun blankOverrideResolvesToPlatformDefault() {
        val settings = MapSettings()
        settings.putString(DownloadsPreferences.KEY_DOWNLOAD_DIRECTORY, "   ")

        assertEquals(defaultDownloadDirectory(), viewModel(settings).directory.value)
    }

    @Test
    fun clearCompletedDelegatesToEngine() {
        val engine = FakeDownloadEngine()
        val viewModel = viewModel(engine = engine)

        viewModel.clearCompleted()

        assertEquals(1, engine.clearCompletedCalls)
    }

    @Test
    fun engineAvailabilityIsExposed() {
        val viewModel = viewModel(engine = FakeDownloadEngine(isAvailable = false))

        assertEquals(false, viewModel.isEngineAvailable)
    }
}

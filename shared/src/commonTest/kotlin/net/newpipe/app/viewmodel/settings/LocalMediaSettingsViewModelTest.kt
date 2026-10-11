/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.newpipe.app.player.LocalMediaFile
import net.newpipe.app.player.LocalMediaScanner
import net.newpipe.app.preferences.LibraryPreferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun mediaFile(path: String): LocalMediaFile = LocalMediaFile(
    id = path,
    path = path,
    name = path.substringAfterLast('/'),
    extension = "mp4",
    mimeType = "video/mp4",
    url = "file://$path"
)

private class FakeLocalMediaScanner(
    override val isAvailable: Boolean = true,
    private val files: List<LocalMediaFile> = emptyList(),
    private val failOn: Set<String> = emptySet()
) : LocalMediaScanner {

    var scanCalls: Int = 0
        private set

    override suspend fun scanFolder(folderPath: String): List<LocalMediaFile> {
        scanCalls++
        if (folderPath in failOn) throw IllegalStateException("cannot read $folderPath")
        return files
    }
}

/** Scanner whose scan suspends on [gate] so a concurrent rescan can be observed. */
private class GatedLocalMediaScanner(
    override val isAvailable: Boolean = true
) : LocalMediaScanner {

    val gate = CompletableDeferred<Unit>()

    var scanCalls: Int = 0
        private set

    override suspend fun scanFolder(folderPath: String): List<LocalMediaFile> {
        scanCalls++
        gate.await()
        return emptyList()
    }
}

class LocalMediaSettingsViewModelTest {

    private fun viewModel(
        settings: MapSettings = MapSettings(),
        scanner: LocalMediaScanner = FakeLocalMediaScanner(),
        pickFolder: suspend () -> String? = { null }
    ) = LocalMediaSettingsViewModel(settings, scanner, pickFolder)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- pure folder blob codec -------------------------------------------------------------

    @Test
    fun pureDecodeTrimsDropsBlanksAndDedupes() {
        assertEquals(
            listOf("/a", "/b", "/c"),
            LocalMediaSettingsViewModel.pureDecodeFolders("/a\n  /b  \n/a\n\n/c\n   ")
        )
    }

    @Test
    fun pureDecodeOfBlankBlobIsEmpty() {
        assertTrue(LocalMediaSettingsViewModel.pureDecodeFolders("").isEmpty())
        assertTrue(LocalMediaSettingsViewModel.pureDecodeFolders("   \n \n").isEmpty())
    }

    @Test
    fun pureEncodeJoinsWithFolderSeparator() {
        assertEquals(
            "/a\n/b\n/c",
            LocalMediaSettingsViewModel.pureEncodeFolders(listOf("/a", "/b", "/c"))
        )
    }

    @Test
    fun pureEncodeDecodeRoundTrip() {
        val folders = listOf("/one", "/two")
        assertEquals(
            folders,
            LocalMediaSettingsViewModel.pureDecodeFolders(
                LocalMediaSettingsViewModel.pureEncodeFolders(folders)
            )
        )
    }

    @Test
    fun pureAppendTrimsAndAppends() {
        assertEquals(
            listOf("/a", "/b"),
            LocalMediaSettingsViewModel.pureAppendFolder(listOf("/a"), "  /b  ")
        )
    }

    @Test
    fun pureAppendIgnoresBlankAndDuplicate() {
        val folders = listOf("/a")
        assertEquals(folders, LocalMediaSettingsViewModel.pureAppendFolder(folders, "   "))
        assertEquals(folders, LocalMediaSettingsViewModel.pureAppendFolder(folders, "/a"))
        assertEquals(folders, LocalMediaSettingsViewModel.pureAppendFolder(folders, "  /a  "))
    }

    @Test
    fun pureRemoveDropsMatchingEntry() {
        assertEquals(
            listOf("/a", "/c"),
            LocalMediaSettingsViewModel.pureRemoveFolder(listOf("/a", "/b", "/c"), "/b")
        )
    }

    @Test
    fun pureRemoveMissingEntryIsNoOp() {
        val folders = listOf("/a")
        assertEquals(folders, LocalMediaSettingsViewModel.pureRemoveFolder(folders, "/nope"))
    }

    // --- loading and persistence ------------------------------------------------------------

    @Test
    fun scannerAvailabilityIsExposed() {
        assertFalse(viewModel(scanner = FakeLocalMediaScanner(isAvailable = false)).isScannerAvailable)
        assertTrue(viewModel(scanner = FakeLocalMediaScanner(isAvailable = true)).isScannerAvailable)
    }

    @Test
    fun foldersLoadFromPersistedBlob() {
        val settings = MapSettings()
        settings.putString(LibraryPreferences.KEY_INDEXED_FOLDERS, "/a\n/b\n/a\n  /c  \n")

        assertEquals(
            listOf("/a", "/b", "/c"),
            viewModel(settings = settings).folders.value
        )
    }

    @Test
    fun addFolderPersistsChosenPath() = runTest {
        val settings = MapSettings()
        val vm = viewModel(settings = settings) { "  /picked  " }

        vm.addFolder()

        assertEquals(listOf("/picked"), vm.folders.value)
        assertEquals("/picked", settings.getString(LibraryPreferences.KEY_INDEXED_FOLDERS, ""))
    }

    @Test
    fun addFolderIgnoresDuplicate() = runTest {
        val settings = MapSettings()
        settings.putString(LibraryPreferences.KEY_INDEXED_FOLDERS, "/existing")
        val vm = viewModel(settings = settings) { "/existing" }

        vm.addFolder()

        assertEquals(listOf("/existing"), vm.folders.value)
    }

    @Test
    fun addFolderCancelledIsNoOp() = runTest {
        val settings = MapSettings()
        val vm = viewModel(settings = settings) { null }

        vm.addFolder()

        assertTrue(vm.folders.value.isEmpty())
        assertEquals("", settings.getString(LibraryPreferences.KEY_INDEXED_FOLDERS, ""))
    }

    @Test
    fun removeFolderPersists() {
        val settings = MapSettings()
        settings.putString(LibraryPreferences.KEY_INDEXED_FOLDERS, "/a\n/b")
        val vm = viewModel(settings = settings)

        vm.removeFolder("/a")

        assertEquals(listOf("/b"), vm.folders.value)
        assertEquals("/b", settings.getString(LibraryPreferences.KEY_INDEXED_FOLDERS, ""))
    }

    @Test
    fun refreshReReadsPersistedFolders() {
        val settings = MapSettings()
        val vm = viewModel(settings = settings)
        assertTrue(vm.folders.value.isEmpty())

        settings.putString(LibraryPreferences.KEY_INDEXED_FOLDERS, "/external\n/added")
        vm.refresh()

        assertEquals(listOf("/external", "/added"), vm.folders.value)
    }

    // --- scanning ---------------------------------------------------------------------------

    @Test
    fun rescanCountsFilesFromEveryFolder() = runTest {
        val settings = MapSettings()
        settings.putString(LibraryPreferences.KEY_INDEXED_FOLDERS, "/a\n/b")
        val scanner = FakeLocalMediaScanner(
            files = listOf(mediaFile("/a/1.mp4"), mediaFile("/a/2.mp4"))
        )
        val vm = viewModel(settings = settings, scanner = scanner)

        vm.rescan()

        assertEquals(4, vm.fileCount.value)
        assertTrue(vm.hasScanned.value)
        assertNull(vm.scanError.value)
        assertFalse(vm.isScanning.value)
    }

    @Test
    fun rescanWithoutFoldersCompletesWithZero() = runTest {
        val vm = viewModel()

        vm.rescan()

        assertEquals(0, vm.fileCount.value)
        assertTrue(vm.hasScanned.value)
        assertNull(vm.scanError.value)
    }

    @Test
    fun rescanReportsUnreadableFolderHonestly() = runTest {
        val settings = MapSettings()
        settings.putString(LibraryPreferences.KEY_INDEXED_FOLDERS, "/good\n/bad")
        val scanner = FakeLocalMediaScanner(
            files = listOf(mediaFile("/good/1.mp4")),
            failOn = setOf("/bad")
        )
        val vm = viewModel(settings = settings, scanner = scanner)

        vm.rescan()

        assertEquals(1, vm.fileCount.value)
        val error = requireNotNull(vm.scanError.value)
        assertTrue(error.contains("/bad"))
        assertFalse(vm.isScanning.value)
    }

    @Test
    fun rescanClearsErrorWhenItLaterSucceeds() = runTest {
        val settings = MapSettings()
        settings.putString(LibraryPreferences.KEY_INDEXED_FOLDERS, "/bad")
        val scanner = FakeLocalMediaScanner(failOn = setOf("/bad"))
        val vm = viewModel(settings = settings, scanner = scanner)

        vm.rescan()
        assertNotNull(vm.scanError.value)

        vm.removeFolder("/bad")
        vm.rescan()

        assertTrue(vm.folders.value.isEmpty())
        assertNull(vm.scanError.value)
    }

    @Test
    fun rescanIsNoOpWhileAlreadyScanning() = runTest {
        val settings = MapSettings()
        settings.putString(LibraryPreferences.KEY_INDEXED_FOLDERS, "/a")
        val scanner = GatedLocalMediaScanner()
        val vm = viewModel(settings = settings, scanner = scanner)

        vm.rescan()
        assertTrue(vm.isScanning.value)

        vm.rescan()
        assertEquals(1, scanner.scanCalls)

        scanner.gate.complete(Unit)
        assertFalse(vm.isScanning.value)
        assertEquals(1, scanner.scanCalls)
    }
}

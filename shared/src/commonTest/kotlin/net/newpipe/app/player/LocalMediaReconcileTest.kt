/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalMediaReconcileTest {

    private fun file(path: String, size: Long = 1L, modified: Long = 1L) = LocalMediaFile(
        id = path,
        path = path,
        name = path.substringAfterLast('/'),
        extension = "mp4",
        mimeType = "video/mp4",
        url = "file://$path",
        sizeBytes = size,
        modifiedAt = modified
    )

    // ---- isUnderFolder ----

    @Test
    fun fileDirectlyInFolderIsUnder() {
        assertTrue(isUnderFolder("/media/a.mp4", "/media"))
    }

    @Test
    fun fileInSubfolderIsUnder() {
        assertTrue(isUnderFolder("/media/sub/a.mp4", "/media"))
    }

    @Test
    fun folderWithTrailingSeparatorIsUnder() {
        assertTrue(isUnderFolder("/media/sub/a.mp4", "/media/"))
    }

    @Test
    fun siblingPrefixIsNotUnder() {
        assertFalse(isUnderFolder("/mediax/a.mp4", "/media"))
    }

    @Test
    fun unrelatedPathIsNotUnder() {
        assertFalse(isUnderFolder("/other/a.mp4", "/media"))
    }

    @Test
    fun windowsSeparatorsAreHandled() {
        assertTrue(isUnderFolder("C:\\media\\a.mp4", "C:\\media"))
    }

    // ---- reconcileScanned ----

    @Test
    fun presentFilesAreKeptAndNotMissing() {
        val a = file("/media/a.mp4")
        val result = reconcileScanned(
            previous = listOf(a),
            current = listOf(a),
            scannedFolders = listOf("/media")
        )
        assertEquals(listOf(a), result)
        assertFalse(result.single().missing)
    }

    @Test
    fun absentFileInScannedFolderIsKeptAndFlaggedMissing() {
        val a = file("/media/a.mp4")
        val result = reconcileScanned(
            previous = listOf(a),
            current = emptyList(),
            scannedFolders = listOf("/media")
        )
        assertEquals(1, result.size)
        assertTrue(result.single().missing)
        assertEquals("/media/a.mp4", result.single().path)
    }

    @Test
    fun absentFileInUnscannedFolderIsCarriedOverUnchanged() {
        val a = file("/media/a.mp4")
        val result = reconcileScanned(
            previous = listOf(a),
            current = emptyList(),
            scannedFolders = emptyList()
        )
        assertEquals(1, result.size)
        assertFalse(result.single().missing)
    }

    @Test
    fun reappearingFileClearsMissingFlag() {
        val missing = file("/media/a.mp4").copy(missing = true)
        val reappeared = file("/media/a.mp4")
        val result = reconcileScanned(
            previous = listOf(missing),
            current = listOf(reappeared),
            scannedFolders = listOf("/media")
        )
        assertEquals(1, result.size)
        assertFalse(result.single().missing)
    }

    @Test
    fun currentItemsComeFirstThenMissingCarriedOver() {
        val kept = file("/media/keep.mp4")
        val gone = file("/media/gone.mp4")
        val result = reconcileScanned(
            previous = listOf(kept, gone),
            current = listOf(kept),
            scannedFolders = listOf("/media")
        )
        assertEquals(listOf("/media/keep.mp4", "/media/gone.mp4"), result.map { it.path })
        assertFalse(result[0].missing)
        assertTrue(result[1].missing)
    }

    @Test
    fun changedFileIsReportedFromCurrent() {
        val old = file("/media/a.mp4", size = 1L)
        val new = file("/media/a.mp4", size = 2L)
        val result = reconcileScanned(
            previous = listOf(old),
            current = listOf(new),
            scannedFolders = listOf("/media")
        )
        assertEquals(2L, result.single().sizeBytes)
        assertFalse(result.single().missing)
    }
}

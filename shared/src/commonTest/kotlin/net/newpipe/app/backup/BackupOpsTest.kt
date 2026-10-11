/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupOpsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun encodedManifest(
        appName: String = BackupOps.APP_NAME,
        schemaVersion: Int = BackupOps.SCHEMA_VERSION,
        createdAtEpochMs: Long = 42L,
        entries: List<String> = listOf("queue.json")
    ): String = json.encodeToString(
        BackupManifest(
            appName = appName,
            schemaVersion = schemaVersion,
            createdAtEpochMs = createdAtEpochMs,
            entries = entries
        )
    )

    @Test
    fun fileNameForUnixEpoch() {
        assertEquals(
            "npp-backup-1970-01-01-00-00-00.nppbackup",
            BackupOps.backupFileName(0L)
        )
    }

    @Test
    fun fileNameForKnownTimestamp() {
        assertEquals(
            "npp-backup-2023-11-14-22-13-20.nppbackup",
            BackupOps.backupFileName(1_700_000_000_000L)
        )
    }

    @Test
    fun fileNameUsesUtcAndZeroPadding() {
        assertEquals(
            "npp-backup-2020-01-02-03-04-05.nppbackup",
            BackupOps.backupFileName(1_577_934_245_000L)
        )
    }

    @Test
    fun fileNamesSortChronologicallyAsStrings() {
        val earlier = BackupOps.backupFileName(1_700_000_000_000L)
        val later = BackupOps.backupFileName(1_700_000_001_000L)
        val muchLater = BackupOps.backupFileName(1_800_000_000_000L)
        assertTrue(earlier < later, "one second later must sort after")
        assertTrue(later < muchLater, "later year must sort after")
    }

    @Test
    fun validManifestIsAccepted() {
        val decoded = BackupOps.validateBackup(encodedManifest())
        assertEquals(BackupOps.APP_NAME, decoded?.appName)
        assertEquals(BackupOps.SCHEMA_VERSION, decoded?.schemaVersion)
        assertEquals(listOf("queue.json"), decoded?.entries)
    }

    @Test
    fun corruptJsonIsRejected() {
        assertNull(BackupOps.validateBackup("{ this is not json"))
    }

    @Test
    fun blankManifestIsRejected() {
        assertNull(BackupOps.validateBackup("   "))
    }

    @Test
    fun wrongAppNameIsRejected() {
        assertNull(BackupOps.validateBackup(encodedManifest(appName = "SomeOtherApp")))
    }

    @Test
    fun wrongSchemaVersionIsRejected() {
        assertNull(BackupOps.validateBackup(encodedManifest(schemaVersion = 99)))
    }

    @Test
    fun describeEntriesPairsNamesWithByteCounts() {
        val manifest = BackupManifest(
            createdAtEpochMs = 1L,
            entries = listOf("queue.json", "playlists.json")
        )
        assertEquals(
            listOf("queue.json" to 10L, "playlists.json" to 2048L),
            BackupOps.describeEntries(manifest, listOf(10, 2048))
        )
    }

    @Test
    fun describeEntriesDropsEntriesWithoutByteCountAndIgnoresExtras() {
        val manifest = BackupManifest(
            createdAtEpochMs = 1L,
            entries = listOf("a.json", "b.json")
        )
        assertEquals(listOf("a.json" to 5L), BackupOps.describeEntries(manifest, listOf(5)))
        assertEquals(
            listOf("a.json" to 5L, "b.json" to 7L),
            BackupOps.describeEntries(manifest, listOf(5, 7, 9))
        )
    }

    @Test
    fun describeEntriesIsEmptyForEmptyManifest() {
        val manifest = BackupManifest(createdAtEpochMs = 1L, entries = emptyList())
        assertTrue(BackupOps.describeEntries(manifest, listOf(1, 2)).isEmpty())
    }
}

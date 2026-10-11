/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Pure backing logic for the Backup & Restore center. Everything here is deterministic and free of
 * I/O so it can be unit-tested directly: file naming, manifest validation and the "View contents"
 * projection.
 */
object BackupOps {

    /** Name of the reserved manifest entry; never listed as user payload. */
    const val MANIFEST_ENTRY = "manifest.json"

    /** Suffix shared by every backup archive, and the filter used when listing a folder. */
    const val FILE_SUFFIX = ".nppbackup"

    /** Application marker expected in a valid [BackupManifest]. */
    const val APP_NAME = "NewPipe+"

    /** Archive layout version this build produces and accepts. */
    const val SCHEMA_VERSION = 1

    private const val FILE_PREFIX = "npp-backup-"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Builds the sortable archive file name for [nowEpochMs]:
     * `npp-backup-yyyy-MM-dd-HH-mm-ss.nppbackup` in UTC. Zero-padded fields make plain string
     * ordering match chronological ordering.
     */
    fun backupFileName(nowEpochMs: Long): String {
        val secondsTotal = floorDiv(nowEpochMs, 1_000L)
        val days = floorDiv(secondsTotal, 86_400L)
        val secondOfDay = secondsTotal - days * 86_400L
        val hour = (secondOfDay / 3_600L).toInt()
        val minute = ((secondOfDay % 3_600L) / 60L).toInt()
        val second = (secondOfDay % 60L).toInt()
        val (year, month, day) = civilFromDays(days)
        return buildString {
            append(FILE_PREFIX)
            append(pad(year, 4)); append('-')
            append(pad(month, 2)); append('-')
            append(pad(day, 2)); append('-')
            append(pad(hour, 2)); append('-')
            append(pad(minute, 2)); append('-')
            append(pad(second, 2))
            append(FILE_SUFFIX)
        }
    }

    /**
     * Parses and validates a manifest JSON string. Returns the manifest only when it declares the
     * expected [APP_NAME] and [SCHEMA_VERSION]; malformed JSON, a foreign app or a future schema all
     * yield `null` so the caller can refuse the archive without touching payload data.
     */
    fun validateBackup(decodedManifestJson: String): BackupManifest? {
        if (decodedManifestJson.isBlank()) return null
        val manifest = try {
            json.decodeFromString<BackupManifest>(decodedManifestJson)
        } catch (_: Exception) {
            return null
        }
        if (manifest.appName != APP_NAME) return null
        if (manifest.schemaVersion != SCHEMA_VERSION) return null
        return manifest
    }

    /**
     * Pairs each manifest entry with its byte count for the "View contents" list. The lists are
     * matched by position; extra byte counts are ignored and entries without a count are dropped, so
     * a truncated/inconsistent input can never crash the UI.
     */
    fun describeEntries(
        manifest: BackupManifest,
        byteCounts: List<Int>
    ): List<Pair<String, Long>> =
        manifest.entries.mapIndexedNotNull { index, name ->
            byteCounts.getOrNull(index)?.let { name to it.toLong() }
        }

    private fun pad(value: Int, width: Int): String = value.toString().padStart(width, '0')

    private fun floorDiv(a: Long, b: Long): Long {
        val quotient = a / b
        return if (a % b != 0L && (a xor b) < 0L) quotient - 1L else quotient
    }

    /**
     * Converts days-since-Unix-epoch to a (`year`, `month`, `day`) UTC triple using Howard Hinnant's
     * `civil_from_days` algorithm. Pure integer math, valid for any epoch day.
     */
    private fun civilFromDays(epochDays: Long): Triple<Int, Int, Int> {
        val z = epochDays + 719_468L
        val era = (if (z >= 0L) z else z - 146_096L) / 146_097L
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
        val year = yoe + era * 400L
        val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
        val mp = (5L * doy + 2L) / 153L
        val day = doy - (153L * mp + 2L) / 5L + 1L
        val month = if (mp < 10L) mp + 3L else mp - 9L
        val correctedYear = if (month <= 2L) year + 1L else year
        return Triple(correctedYear.toInt(), month.toInt(), day.toInt())
    }
}

/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

import kotlinx.serialization.Serializable

/**
 * Self-describing header stored as the first entry (`manifest.json`) of every backup archive.
 *
 * The manifest lets a reader reject foreign archives before touching any payload: [appName] and
 * [schemaVersion] must match, otherwise the file is not a NewPipe+ backup this build understands.
 *
 * @param appName Owning application marker, checked against [BackupOps.APP_NAME].
 * @param schemaVersion Archive layout version, checked against [BackupOps.SCHEMA_VERSION].
 * @param createdAtEpochMs Creation time (UTC epoch millis); also drives the archive file name.
 * @param entries Payload entry names, in the order they were written.
 */
@Serializable
data class BackupManifest(
    val appName: String = "NewPipe+",
    val schemaVersion: Int = 1,
    val createdAtEpochMs: Long,
    val entries: List<String>
)

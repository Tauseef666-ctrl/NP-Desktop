/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.newpipe.app.player.LocalMediaFile
import net.newpipe.app.player.LocalMediaFormats
import net.newpipe.app.player.LocalMediaScanner
import net.newpipe.app.player.isUnderFolder
import org.koin.core.annotation.Singleton

/**
 * Android folder scanner backed by the media provider.
 *
 * It lists the audio and video items the system has indexed, then keeps the ones that live under
 * the requested folder. Only what the media store already exposes is ever returned: unreadable or
 * not-yet-indexed files are simply absent rather than guessed, and a failing provider query degrades
 * to an empty result instead of crashing. No file is ever modified.
 */
@Singleton(binds = [LocalMediaScanner::class])
class AndroidLocalMediaScanner(private val context: Context) : LocalMediaScanner {

    override val isAvailable: Boolean = true

    override suspend fun scanFolder(folderPath: String): List<LocalMediaFile> =
        withContext(Dispatchers.IO) {
            val byPath = LinkedHashMap<String, LocalMediaFile>()
            collect(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, folderPath, audioOnly = false, out = byPath)
            collect(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, folderPath, audioOnly = true, out = byPath)
            byPath.values.toList()
        }

    private fun collect(
        collection: Uri,
        folderPath: String,
        audioOnly: Boolean,
        out: MutableMap<String, LocalMediaFile>
    ) {
        val base = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val metadata = base + buildList {
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.LATITUDE)
            add(MediaStore.MediaColumns.LONGITUDE)
            if (audioOnly) {
                add(MediaStore.Audio.Media.ARTIST)
                add(MediaStore.Audio.Media.ALBUM)
            }
        }
        val cursor = query(collection, metadata) ?: query(collection, base) ?: return

        cursor.use { c ->
            val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val modifiedIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeIdx = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val widthIdx = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightIdx = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val latitudeIdx = c.getColumnIndex(MediaStore.MediaColumns.LATITUDE)
            val longitudeIdx = c.getColumnIndex(MediaStore.MediaColumns.LONGITUDE)
            val artistIdx = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumIdx = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            if (dataIdx < 0) return

            while (c.moveToNext()) {
                val path = c.getString(dataIdx)?.takeIf { it.isNotBlank() } ?: continue
                if (!isUnderFolder(path, folderPath)) continue

                val name = nameIdx.takeIf { it >= 0 }?.let { c.getString(it) }
                    ?.takeIf { it.isNotBlank() }
                    ?: File(path).name
                val extension = name.substringAfterLast('.', "").lowercase()
                val size = sizeIdx.takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L
                val modifiedSeconds = modifiedIdx.takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L
                val mime = mimeIdx.takeIf { it >= 0 }?.let { c.getString(it) }
                    ?.takeIf { it.isNotBlank() }
                    ?: LocalMediaFormats.mimeType(extension)

                out[path] = LocalMediaFile(
                    id = path,
                    path = path,
                    name = name,
                    extension = extension,
                    mimeType = mime,
                    url = Uri.fromFile(File(path)).toString(),
                    sizeBytes = size,
                    modifiedAt = modifiedSeconds * 1000L,
                    durationMs = null,
                    audioOnly = audioOnly,
                    width = widthIdx.positiveInt(c),
                    height = heightIdx.positiveInt(c),
                    artist = artistIdx.nonBlankString(c),
                    album = albumIdx.nonBlankString(c),
                    latitude = latitudeIdx.let { if (it >= 0 && !c.isNull(it)) c.getDouble(it) else null },
                    longitude = longitudeIdx.let { if (it >= 0 && !c.isNull(it)) c.getDouble(it) else null }
                )
            }
        }
    }

    private fun query(collection: Uri, projection: Array<String>): android.database.Cursor? =
        runCatching {
            context.contentResolver.query(collection, projection, null, null, null)
        }.getOrNull()

    private fun Int.positiveInt(cursor: android.database.Cursor): Int? =
        if (this >= 0) cursor.getInt(this).takeIf { it > 0 } else null

    private fun Int.nonBlankString(cursor: android.database.Cursor): String? =
        if (this >= 0) cursor.getString(this)?.takeIf { it.isNotBlank() } else null
}

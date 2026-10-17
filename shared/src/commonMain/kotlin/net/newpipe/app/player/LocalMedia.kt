/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

/** Container/extension classification for local files. Pure and shared by scanners and UI. */
object LocalMediaFormats {

    val videoExtensions: Set<String> = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "mpg", "mpeg", "wmv", "flv", "3gp", "ts", "ogv"
    )

    val audioExtensions: Set<String> = setOf(
        "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "wma"
    )

    val supportedExtensions: Set<String> = videoExtensions + audioExtensions

    fun isSupported(extension: String): Boolean = extension.lowercase() in supportedExtensions

    fun isAudio(extension: String): Boolean = extension.lowercase() in audioExtensions

    fun mimeType(extension: String): String = when (extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "mpg", "mpeg" -> "video/mpeg"
        "wmv" -> "video/x-ms-wmv"
        "flv" -> "video/x-flv"
        "3gp" -> "video/3gpp"
        "ts" -> "video/mp2t"
        "ogv" -> "video/ogg"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "oga" -> "audio/ogg"
        "opus" -> "audio/opus"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "wma" -> "audio/x-ms-wma"
        else -> "application/octet-stream"
    }
}

/**
 * A single media file discovered in an indexed folder.
 *
 * @param id Stable identity (the absolute path).
 * @param path Human-readable location, shown in the UI.
 * @param url Engine-playable location (e.g. a `file://` URI).
 * @param durationMs Known duration, or null when not yet determined (never faked).
 * @param missing True when the file was indexed before but is gone from disk; kept so nothing is
 *   silently lost and the row can be flagged instead of vanishing.
 * @param width Video width in pixels, or null when the scanner could not determine it.
 * @param height Video height in pixels, or null when the scanner could not determine it.
 * @param artist Track artist for audio files, or null when unknown.
 * @param album Album name for audio files, or null when unknown.
 * @param latitude Geographic latitude in degrees, or null when the file carries no location.
 * @param longitude Geographic longitude in degrees, or null when the file carries no location.
 */
data class LocalMediaFile(
    val id: String,
    val path: String,
    val name: String,
    val extension: String,
    val mimeType: String,
    val url: String,
    val sizeBytes: Long = 0L,
    val modifiedAt: Long = 0L,
    val durationMs: Long? = null,
    val audioOnly: Boolean = false,
    val missing: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val artist: String? = null,
    val album: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val hasKnownDuration: Boolean get() = durationMs != null && durationMs > 0L

    fun toMediaItem(): MediaItem = MediaItem(
        id = id,
        title = name,
        url = url,
        durationMs = durationMs ?: 0L,
        audioOnly = audioOnly
    )
}

/**
 * Formats [width]×[height] as a resolution label (for example `1920×1080`), or null when either
 * dimension is missing or non-positive. Pure and platform-free so it is unit-testable.
 */
fun formatResolution(width: Int?, height: Int?): String? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    return "$width×$height"
}

/**
 * A short, human-readable metadata line for [file], joining only the fields the scanner actually
 * determined (resolution, artist/album, and a location pin). Returns null when nothing is known, so
 * the UI can omit the line rather than print placeholders.
 */
fun localMediaMetaLine(file: LocalMediaFile): String? {
    val resolution = formatResolution(file.width, file.height)
    val credits = listOfNotNull(
        file.artist?.takeIf { it.isNotBlank() },
        file.album?.takeIf { it.isNotBlank() }
    ).joinToString(" – ").takeIf { it.isNotBlank() }
    val location = if (file.latitude != null && file.longitude != null) {
        "📍 ${file.latitude}, ${file.longitude}"
    } else {
        null
    }
    return listOfNotNull(resolution, credits, location).takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

/**
 * Result of comparing two folder scans. Every list preserves the order of the input it was drawn
 * from; [removed] holds the absolute paths that disappeared.
 */
data class ScanDiff(
    val added: List<LocalMediaFile>,
    val removed: List<String>,
    val changed: List<LocalMediaFile>,
    val unchanged: List<LocalMediaFile>
)

/**
 * Pure, IO-free comparison of two scans keyed by absolute [LocalMediaFile.path]. A file counts as
 * [ScanDiff.changed] when its modification time or size differs, and the `current` entry is the one
 * reported.
 */
fun diffByPath(previous: List<LocalMediaFile>, current: List<LocalMediaFile>): ScanDiff {
    val previousByPath = previous.associateBy { it.path }
    val currentByPath = current.associateBy { it.path }

    val added = current.filter { it.path !in previousByPath }
    val removed = previous.map { it.path }.filter { it !in currentByPath }
    val changed = mutableListOf<LocalMediaFile>()
    val unchanged = mutableListOf<LocalMediaFile>()

    for (file in current) {
        val old = previousByPath[file.path] ?: continue
        if (old.modifiedAt != file.modifiedAt || old.sizeBytes != file.sizeBytes) {
            changed += file
        } else {
            unchanged += file
        }
    }

    return ScanDiff(added = added, removed = removed, changed = changed, unchanged = unchanged)
}

/**
 * True when [path] is [folder] itself or lives below it. Separator-agnostic so it works for both
 * POSIX (`/`) and Windows (`\`) paths, and avoids the `/a/b` vs `/a/bc` prefix trap.
 */
fun isUnderFolder(path: String, folder: String): Boolean {
    val trimmed = folder.trimEnd('/', '\\')
    if (trimmed.isEmpty()) return path.startsWith("/") || path.startsWith("\\")
    return path == trimmed || path.startsWith("$trimmed/") || path.startsWith("$trimmed\\")
}

/**
 * Reconciles a fresh scan with the previously known files, without ever losing an entry.
 *
 * Files present in [current] are returned as-is. Files that were known before but are absent now
 * are kept and flagged [LocalMediaFile.missing] *only* when their folder was actually scanned this
 * pass ([scannedFolders]); entries belonging to a folder that failed to scan are carried over
 * unchanged, so an unreadable folder can never hide its files.
 */
fun reconcileScanned(
    previous: List<LocalMediaFile>,
    current: List<LocalMediaFile>,
    scannedFolders: List<String>
): List<LocalMediaFile> {
    val currentPaths = current.mapTo(HashSet()) { it.path }
    val carriedOver = previous
        .filter { it.path !in currentPaths }
        .map { file ->
            if (scannedFolders.any { isUnderFolder(file.path, it) }) {
                file.copy(missing = true)
            } else {
                file
            }
        }
    return current + carriedOver
}

/**
 * Scans user-selected folders for playable files. Never mutates the filesystem; a missing or
 * unreadable folder is surfaced as an error, not silently skipped.
 */
interface LocalMediaScanner {

    /** False when the platform has no folder-scanning implementation yet. */
    val isAvailable: Boolean

    /** Recursively list supported media in [folderPath]. Throws when the folder cannot be read. */
    suspend fun scanFolder(folderPath: String): List<LocalMediaFile>
}

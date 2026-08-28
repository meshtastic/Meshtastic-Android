/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.app.map

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.ioDispatcher
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Where imported MBTiles archives live, kept apart from the imported-overlay directory. */
private const val MBTILES_DIR = "mbtiles"
private const val TAG = "MbTilesImport"

/**
 * Copies a picked MBTiles archive into app storage and returns its absolute path.
 *
 * The copy is not optional. A document picker hands back a `content://` URI backed by another app's provider, and
 * MapLibre's MBTiles source opens a path with SQLite directly — it says so itself, in the one error string it carries:
 * "MBTilesFileSource only supports absolute path urls". So the file has to be somewhere this process can name.
 *
 * Returns null if the copy fails, so a source is never stored pointing at a file that was never written.
 */
internal suspend fun importMbTiles(context: Context, uri: Uri, fileName: String): String? = withContext(ioDispatcher) {
    // Every call below blocks, and an MBTiles archive is routinely hundreds of megabytes, so running on the
    // caller's dispatcher would freeze the UI for the whole copy — `suspend` alone does not move work off it.
    try {
        val input = context.contentResolver.openInputStream(uri)
        if (input == null) {
            Logger.withTag(TAG).w { "Could not open the picked MBTiles file" }
            null
        } else {
            val directory = File(context.filesDir, MBTILES_DIR).apply { if (!exists()) mkdirs() }
            val target = File(directory, fileName)
            input.use { source -> FileOutputStream(target).use { sink -> source.copyTo(sink) } }
            target.absolutePath
        }
    } catch (e: IOException) {
        Logger.withTag(TAG).e(e) { "Could not copy the picked MBTiles file into app storage" }
        null
    } catch (e: SecurityException) {
        // Not an IOException: the picker's URI grant can be gone by the time the copy runs, and the throw would
        // otherwise escape a function whose contract is to return null on failure.
        Logger.withTag(TAG).e(e) { "Lost permission to read the picked MBTiles file" }
        null
    }
}

/** The URL form MapLibre's MBTiles source accepts: its own scheme over an absolute path, with no tile placeholders. */
internal fun mbTilesUrl(absolutePath: String): String = "mbtiles://$absolutePath"

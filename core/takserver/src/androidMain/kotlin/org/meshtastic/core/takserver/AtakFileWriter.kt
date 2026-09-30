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
package org.meshtastic.core.takserver

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import co.touchlab.kermit.Logger
import org.meshtastic.core.common.ContextServices
import java.io.File
import java.io.IOException

internal actual object AtakFileWriter {

    @Suppress("TooGenericExceptionCaught")
    actual fun writeToImportDir(fileName: String, zipBytes: ByteArray): Boolean {
        // Sanitize: fileName originates from untrusted mesh CoT uid attributes.
        val safeName = fileName.replace(UNSAFE_FILE_NAME_CHARS, "_")
        return try {
            val context = ContextServices.app
            val location =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    writeToSharedDownloads(context, safeName, zipBytes).toString()
                } else {
                    // Shared storage needs WRITE_EXTERNAL_STORAGE below API 29; the app's own external dir needs none.
                    writeToAppExternalDownloads(context, safeName, zipBytes)
                }
            Logger.i { "Route data package written: $safeName (${zipBytes.size} bytes) to $location" }
            true
        } catch (e: Exception) {
            // No throwable: platform file errors carry the path, and the name comes from mesh data.
            Logger.e { "Route data package was not saved: ${e::class.simpleName}" }
            false
        }
    }

    /**
     * Route updates overwrite the row this install saved, found by its [MediaStore.Downloads.DOWNLOAD_URI] marker
     * because a reinstall orphans the old row and MediaProvider renames the new one. Rows saved before the marker match
     * by name.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToSharedDownloads(context: Context, name: String, bytes: ByteArray): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val marker = ROUTE_MARKER_PREFIX + name
        val existing =
            resolver
                .query(
                    collection,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.RELATIVE_PATH} = ? AND " +
                        "(${MediaStore.Downloads.DOWNLOAD_URI} = ? OR ${MediaStore.Downloads.DISPLAY_NAME} = ?)",
                    arrayOf(DOWNLOADS_RELATIVE_PATH, marker, name),
                    null,
                )
                ?.use { cursor ->
                    if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
                }
        if (existing != null) {
            resolver.writeBytes(existing, "wt", bytes)
            return existing
        }

        val pending =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, ZIP_MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, DOWNLOADS_RELATIVE_PATH)
                put(MediaStore.Downloads.DOWNLOAD_URI, marker)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val inserted = resolver.insert(collection, pending) ?: throw IOException("MediaStore refused to create $name")
        // A pending row left behind hides this name from the lookup above, so a retry would get a renamed copy.
        var published = false
        try {
            resolver.writeBytes(inserted, "w", bytes)
            resolver.update(inserted, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            published = true
        } finally {
            if (!published) resolver.delete(inserted, null, null)
        }
        return inserted
    }

    private fun writeToAppExternalDownloads(context: Context, name: String, bytes: ByteArray): String {
        val dir =
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IOException("External storage is not available")
        val target = File(dir, name)
        target.writeBytes(bytes)
        return target.absolutePath
    }

    private fun ContentResolver.writeBytes(uri: Uri, mode: String, bytes: ByteArray) {
        val stream = openOutputStream(uri, mode) ?: throw IOException("No output stream for $uri")
        stream.use { it.write(bytes) }
    }

    private val UNSAFE_FILE_NAME_CHARS = Regex("[^a-zA-Z0-9._-]")
    private val DOWNLOADS_RELATIVE_PATH = "${Environment.DIRECTORY_DOWNLOADS}/"
    private const val ZIP_MIME_TYPE = "application/zip"
    private const val ROUTE_MARKER_PREFIX = "meshtastic://atak-route/"
}

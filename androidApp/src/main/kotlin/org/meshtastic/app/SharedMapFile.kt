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
package org.meshtastic.app

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import co.touchlab.kermit.Logger
import org.meshtastic.feature.map.layers.LayerType
import org.meshtastic.feature.map.layers.MAX_KMZ_INFLATED_BYTES
import org.meshtastic.feature.map.layers.resolveLayerType
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** What another app's VIEW or SEND says about a map file, read from its provider before any of the file is. */
internal data class SharedMapFile(
    val scheme: String?,
    val displayName: String,
    val mimeType: String?,
    val size: Long?,
) {
    /** Same precedence as the in-app picker: the name's extension, then the MIME subtype. */
    val extensionOrMime: String?
        get() = displayName.substringAfterLast('.', "").ifBlank { mimeType?.substringAfterLast('/') }

    fun rejection(maxBytes: Long = MAX_SHARED_MAP_FILE_BYTES): SharedMapFileRejection? = when {
        // The app holds no storage permission, so any file:// it can open is its own private data or a system path.
        scheme != ContentResolver.SCHEME_CONTENT -> SharedMapFileRejection.NOT_CONTENT_URI

        resolveLayerType(extensionOrMime) !in SHAREABLE_LAYER_TYPES -> SharedMapFileRejection.UNSUPPORTED_TYPE

        size != null && size > maxBytes -> SharedMapFileRejection.TOO_LARGE

        else -> null
    }
}

internal enum class SharedMapFileRejection {
    NOT_CONTENT_URI,
    UNSUPPORTED_TYPE,
    TOO_LARGE,
    UNREADABLE,
}

/** No bigger than the most the KMZ reader will inflate, so a larger file could never be read whole anyway. */
internal const val MAX_SHARED_MAP_FILE_BYTES: Long = MAX_KMZ_INFLATED_BYTES
internal const val MAX_SHARED_MAP_FILE_MB = (MAX_SHARED_MAP_FILE_BYTES / (1024L * 1024L)).toInt()

private val SHAREABLE_LAYER_TYPES = setOf(LayerType.KML, LayerType.GEOJSON)

private const val TAG = "SharedMapFile"

/** A shared map file read whole, or why it wasn't. */
internal sealed interface SharedMapFileLoad {
    class Loaded(val file: SharedMapFile, val bytes: ByteArray) : SharedMapFileLoad

    data class Refused(val reason: SharedMapFileRejection) : SharedMapFileLoad
}

/**
 * Checks [uri] and reads it at most once, never past [maxBytes]. Blocking provider IPC, so never on the main thread.
 * Nothing is opened when the metadata alone refuses the file.
 */
internal fun ContentResolver.loadSharedMapFile(
    uri: Uri,
    maxBytes: Long = MAX_SHARED_MAP_FILE_BYTES,
): SharedMapFileLoad {
    val file = sharedMapFile(uri) ?: return SharedMapFileLoad.Refused(SharedMapFileRejection.UNREADABLE)
    val refusal = file.rejection(maxBytes)
    return if (refusal != null) SharedMapFileLoad.Refused(refusal) else readSharedMapFile(uri, file, maxBytes)
}

/**
 * Metadata only, null if the provider throws. Nothing is queried for a non-`content://` [uri], which
 * [SharedMapFile.rejection] refuses anyway.
 */
internal fun ContentResolver.sharedMapFile(uri: Uri): SharedMapFile? {
    val fallbackName = uri.lastPathSegment.orEmpty()
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return SharedMapFile(uri.scheme, fallbackName, null, null)
    return try {
        val mimeType = getType(uri)
        val nameAndSize = queryNameAndSize(uri)
        SharedMapFile(uri.scheme, nameAndSize?.first ?: fallbackName, mimeType, nameAndSize?.second)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        // Another app's provider: binder rethrows whatever it throws.
        Logger.withTag(TAG).w(e) { "Shared map file provider failed its metadata query" }
        null
    }
}

private fun ContentResolver.queryNameAndSize(uri: Uri): Pair<String?, Long?>? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
        name to size
    }

/** A reported size is only a claim, so the cap holds on the stream too. */
private fun ContentResolver.readSharedMapFile(uri: Uri, file: SharedMapFile, maxBytes: Long): SharedMapFileLoad = try {
    val stream = openInputStream(uri)
    val bytes = stream?.use { it.readAtMost(maxBytes) }
    when {
        stream == null -> SharedMapFileLoad.Refused(SharedMapFileRejection.UNREADABLE)
        bytes == null -> SharedMapFileLoad.Refused(SharedMapFileRejection.TOO_LARGE)
        else -> SharedMapFileLoad.Loaded(file, bytes)
    }
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    Logger.withTag(TAG).w(e) { "Could not read the shared map file" }
    SharedMapFileLoad.Refused(SharedMapFileRejection.UNREADABLE)
}

/** Every byte of the stream, or null once it runs past [maxBytes]. */
internal fun InputStream.readAtMost(maxBytes: Long): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) return out.toByteArray()
        total += count
        if (total > maxBytes) {
            Logger.withTag(TAG).w { "Refusing a shared map file past the ${maxBytes}B cap" }
            return null
        }
        out.write(buffer, 0, count)
    }
}

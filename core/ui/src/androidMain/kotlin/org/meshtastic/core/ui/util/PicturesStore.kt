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
package org.meshtastic.core.ui.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PICTURES_RELATIVE_PATH = "Pictures/Meshtastic"
private const val JPEG_QUALITY = 92

/**
 * Saves [bitmap] as a JPEG in Pictures/Meshtastic, named after [title] and the time, and returns it, or null when
 * nothing was saved. Android 10 and later only, where writing there needs no storage permission. Blocks on disk, so
 * call it off the main thread.
 */
@RequiresApi(Build.VERSION_CODES.Q)
fun saveJpegToPictures(context: Context, bitmap: Bitmap, title: String): Uri? {
    val resolver = context.contentResolver
    val pending = picturesJpegEntry(title).apply { put(MediaStore.Images.Media.IS_PENDING, 1) }
    val uri =
        runCatching {
            resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), pending)
        }
            .getOrNull() ?: return null
    val written = runCatching {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) } == true &&
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0
    }
        .getOrDefault(false)
    // A row left pending would sit hidden in the gallery, so a failed write removes it.
    if (!written) runCatching { resolver.delete(uri, null, null) }
    return uri.takeIf { written }
}

/** The Pictures/Meshtastic entry [saveJpegToPictures] writes, for a JPEG another writer fills, such as the camera. */
@RequiresApi(Build.VERSION_CODES.Q)
fun picturesJpegEntry(title: String): ContentValues {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "${title.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')}-$stamp.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, PICTURES_RELATIVE_PATH)
    }
}

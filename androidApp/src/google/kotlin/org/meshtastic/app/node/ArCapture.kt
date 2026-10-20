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
package org.meshtastic.app.node

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.view.children
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

private const val PICTURES_RELATIVE_PATH = "Pictures/Meshtastic"
private const val JPEG_QUALITY = 92

/**
 * Copies the AR view's GL surface into Pictures/Meshtastic: the camera feed and the model, none of the Compose controls
 * drawn over it. The scene renders every camera frame, so the surface is never stale. Null when nothing was saved.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal suspend fun captureArScene(root: View, title: String, io: CoroutineDispatcher): Uri? {
    val surface = root.findSurfaceView()?.takeIf { it.width > 0 && it.height > 0 } ?: return null
    val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
    val copied = suspendCancellableCoroutine { continuation ->
        PixelCopy.request(
            surface,
            bitmap,
            { result -> continuation.resume(result == PixelCopy.SUCCESS) },
            Handler(Looper.getMainLooper()),
        )
    }
    return if (copied) withContext(io) { saveToPictures(root.context, bitmap, title) } else null
}

private fun View.findSurfaceView(): SurfaceView? = when (this) {
    is SurfaceView -> this
    is ViewGroup -> children.firstNotNullOfOrNull { it.findSurfaceView() }
    else -> null
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun saveToPictures(context: Context, bitmap: Bitmap, title: String): Uri? {
    val resolver = context.contentResolver
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val name = "${title.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')}-$stamp.jpg"
    val pending =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, PICTURES_RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
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

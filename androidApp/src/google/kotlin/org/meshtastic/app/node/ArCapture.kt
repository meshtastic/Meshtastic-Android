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

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.view.children
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.meshtastic.core.ui.util.saveJpegToPictures
import kotlin.coroutines.resume

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
    return if (copied) withContext(io) { saveJpegToPictures(root.context, bitmap, title) } else null
}

private fun View.findSurfaceView(): SurfaceView? = when (this) {
    is SurfaceView -> this
    is ViewGroup -> children.firstNotNullOfOrNull { it.findSurfaceView() }
    else -> null
}

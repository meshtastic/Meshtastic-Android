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
package org.meshtastic.feature.map.hud

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.Window
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.device_model_capture_failed
import org.meshtastic.core.resources.device_model_capture_saved
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

private const val PICTURES_RELATIVE_PATH = "Pictures/Meshtastic"
private const val JPEG_QUALITY = 92
private const val FILE_TITLE = "Meshtastic mesh view"

/** Android 10 and later only, where saving to Pictures needs no storage permission. */
@Composable
actual fun rememberHudCapture(): (() -> Unit)? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val dispatchers = koinInject<CoroutineDispatchers>()
    val haptic = LocalHapticFeedback.current
    val saved = stringResource(Res.string.device_model_capture_saved)
    val failed = stringResource(Res.string.device_model_capture_failed)
    return {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val uri = view.context.findActivity()?.window?.let { captureWindow(it, dispatchers.io) }
            Toast.makeText(view.context, if (uri != null) saved else failed, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Copies the window as drawn. The camera preview must render into the window ([HudCameraPreview] pins the embedded
 * viewfinder): a SurfaceView sits on its own layer, which a window copy leaves out.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private suspend fun captureWindow(window: Window, io: CoroutineDispatcher): Uri? {
    val decor = window.decorView.takeIf { it.width > 0 && it.height > 0 } ?: return null
    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    val copied = suspendCancellableCoroutine { continuation ->
        PixelCopy.request(
            window,
            bitmap,
            { result -> continuation.resume(result == PixelCopy.SUCCESS) },
            Handler(Looper.getMainLooper()),
        )
    }
    return if (copied) withContext(io) { saveToPictures(decor.context, bitmap, FILE_TITLE) } else null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
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
    val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), pending)
    val written =
        uri != null &&
            runCatching {
                resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            }
                .getOrNull() == true
    // A row left pending would sit hidden in the gallery, so a failed write removes it.
    if (written) {
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    } else if (uri != null) {
        resolver.delete(uri, null, null)
    }
    return uri.takeIf { written }
}

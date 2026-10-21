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
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import co.touchlab.kermit.Logger
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
import org.meshtastic.core.ui.util.saveJpegToPictures
import kotlin.coroutines.resume

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
    // One capture at a time: each holds a full-window bitmap, so a burst of taps must not stack them.
    var capturing by remember { mutableStateOf(false) }
    return {
        if (!capturing) {
            capturing = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                try {
                    val uri = view.context.findActivity()?.window?.let { captureWindow(it, dispatchers.io) }
                    Toast.makeText(view.context, if (uri != null) saved else failed, Toast.LENGTH_SHORT).show()
                } finally {
                    capturing = false
                }
            }
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
        try {
            PixelCopy.request(
                window,
                bitmap,
                { result -> continuation.resume(result == PixelCopy.SUCCESS) },
                Handler(Looper.getMainLooper()),
            )
        } catch (e: IllegalArgumentException) {
            // The window can lose its surface between the size check and the request.
            Logger.w(e) { "Mesh view capture lost its window surface" }
            continuation.resume(false)
        }
    }
    return if (copied) withContext(io) { saveJpegToPictures(decor.context, bitmap, FILE_TITLE) } else null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

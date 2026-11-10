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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraEffect
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.ui.util.picturesJpegEntry
import kotlin.coroutines.resume

private const val FILE_TITLE = "Meshtastic mesh view"
private const val OVERLAY_DRAW_TIMEOUT_MS = 1_000L

/**
 * The mesh view's still: [imageCapture] behind an [OverlayEffect] that draws the overlay recorded at the shutter into
 * the camera frames until the picture is taken. CameraX applies a surface effect to a still only on a stream shared
 * with the preview, so for that moment the drawn overlay shows in the preview too, over the live one it was recorded
 * from.
 */
internal class HudOverlayStill(private val context: Context, private val imageCapture: ImageCapture) :
    HudStill,
    AutoCloseable {
    private val thread = HandlerThread("MeshHudOverlay").apply { start() }
    private val handler = Handler(thread.looper)
    val effect =
        OverlayEffect(
            CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE or CameraEffect.IMAGE_CAPTURE,
            0,
            handler,
        ) {
            Logger.w(it) { "Mesh view overlay effect failed" }
        }

    // Touched only on the handler's thread. An overlay canvas left alone keeps its last drawing, so each still draws
    // once and clears once.
    private var toDraw: Bitmap? = null
    private var drawn: CompletableDeferred<Unit>? = null
    private var showing = false
    private var clear = false
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        effect.setOnDrawListener(::onDraw)
    }

    private fun onDraw(frame: Frame): Boolean {
        val bitmap = toDraw
        when {
            bitmap != null -> {
                toDraw = null
                val canvas = frame.overlayCanvas
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                val crop = frame.cropRect
                matrix.setValues(
                    viewToBufferMatrix(
                        viewWidth = bitmap.width.toFloat(),
                        viewHeight = bitmap.height.toFloat(),
                        cropLeft = crop.left.toFloat(),
                        cropTop = crop.top.toFloat(),
                        cropRight = crop.right.toFloat(),
                        cropBottom = crop.bottom.toFloat(),
                        rotationDegrees = frame.rotationDegrees,
                    ),
                )
                canvas.setMatrix(matrix)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                showing = true
                Logger.d {
                    "Mesh view overlay ${bitmap.width}x${bitmap.height} drawn into a ${frame.size} frame, " +
                        "crop $crop, rotation ${frame.rotationDegrees}"
                }
                drawn?.complete(Unit)
            }

            clear && showing -> {
                frame.overlayCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                showing = false
                clear = false
            }
        }
        return true
    }

    override suspend fun take(overlay: ImageBitmap): Boolean {
        // The effect's canvas is a software one, which cannot draw a hardware bitmap.
        val bitmap = overlay.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        if (bitmap == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val drawn = CompletableDeferred<Unit>()
        handler.post {
            toDraw = bitmap
            clear = false
            this.drawn = drawn
        }
        try {
            val overlaid = withTimeoutOrNull(OVERLAY_DRAW_TIMEOUT_MS) { drawn.await() } != null
            if (!overlaid) Logger.w { "Mesh view overlay not drawn: no camera frame reached the effect" }
            return overlaid && takePicture()
        } finally {
            handler.post {
                toDraw = null
                clear = true
                this.drawn = null
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun takePicture(): Boolean = suspendCancellableCoroutine { continuation ->
        val options =
            ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                picturesJpegEntry(FILE_TITLE),
            )
                .build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Logger.d { "Mesh view still saved at ${output.savedUri?.let(::pixelSize)}" }
                    continuation.resume(true)
                }

                override fun onError(exception: ImageCaptureException) {
                    Logger.w(exception) { "Mesh view still failed" }
                    continuation.resume(false)
                }
            },
        )
    }

    private fun pixelSize(uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        "${bounds.outWidth}x${bounds.outHeight}"
    }
        .getOrNull()

    override fun close() {
        effect.close()
        thread.quitSafely()
    }
}

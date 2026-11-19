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
@file:Suppress(
    // Every js("...") snippet references its Kotlin parameters inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.core.ui.util

import kotlinx.coroutines.await
import kotlin.js.Promise

private const val CAMERA_MAX_SIDE = 480
private const val IMAGE_MAX_SIDE = 1280

/**
 * Finds a QR code in a `<video>` frame or an `ImageBitmap`. Uses the browser's `BarcodeDetector` where it reads QR
 * codes (Chromium on Android, macOS and ChromeOS) and jsQR everywhere else (Chrome on Windows and Linux, Firefox,
 * Safari).
 */
internal class QrDecoder private constructor(private val detector: JsAny?) {
    private val canvas: JsAny by lazy { createCanvas() }

    /**
     * [cropToSquare] reads only the frame's centred square, the part a camera view with `object-fit: cover` shows in
     * the reticle; jsQR then works on fewer pixels, which keeps the main thread free between frames.
     */
    suspend fun decode(source: JsAny, cropToSquare: Boolean): String? = if (detector != null) {
        detectFirst(detector, source).await<JsString?>()?.toString()
    } else {
        decodeWithJsQr(source, cropToSquare)
    }

    private fun decodeWithJsQr(source: JsAny, cropToSquare: Boolean): String? {
        val maxSide = if (cropToSquare) CAMERA_MAX_SIDE else IMAGE_MAX_SIDE
        val pixels = grabPixels(source, canvas, cropToSquare, maxSide) ?: return null
        // Camera frames retry every 200 ms, so they skip jsQR's slower inverted pass; a picked image gets one go.
        val result = jsQR(pixelData(pixels), pixelWidth(pixels), pixelHeight(pixels), jsQrOptions(!cropToSquare))
        return result?.let(::resultText)
    }

    companion object {
        suspend fun create(): QrDecoder =
            QrDecoder(if (qrDetectorAvailable().await<JsBoolean>().toBoolean()) newQrDetector() else null)
    }
}

private fun qrDetectorAvailable(): Promise<JsBoolean> = js(
    """(typeof BarcodeDetector === 'undefined' || typeof BarcodeDetector.getSupportedFormats !== 'function')
        ? Promise.resolve(false)
        : BarcodeDetector.getSupportedFormats().then((f) => f.includes('qr_code'), () => false)""",
)

private fun newQrDetector(): JsAny = js("new BarcodeDetector({ formats: ['qr_code'] })")

private fun detectFirst(detector: JsAny, source: JsAny): Promise<JsString?> =
    js("detector.detect(source).then((codes) => (codes.length ? codes[0].rawValue : null), () => null)")

private fun createCanvas(): JsAny = js("document.createElement('canvas')")

// Draws the source, or its centred square, scaled down to maxSide, and returns its ImageData; null before a video has
// a frame.
private fun grabPixels(source: JsAny, canvas: JsAny, cropToSquare: Boolean, maxSide: Int): JsAny? = js(
    """{
    const w = source.videoWidth || source.width;
    const h = source.videoHeight || source.height;
    if (!w || !h) return null;
    const side = Math.min(w, h);
    const sw = cropToSquare ? side : w;
    const sh = cropToSquare ? side : h;
    const scale = Math.min(1, maxSide / Math.max(sw, sh));
    canvas.width = Math.max(1, Math.round(sw * scale));
    canvas.height = Math.max(1, Math.round(sh * scale));
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    ctx.drawImage(source, (w - sw) / 2, (h - sh) / 2, sw, sh, 0, 0, canvas.width, canvas.height);
    return ctx.getImageData(0, 0, canvas.width, canvas.height);
}""",
)

private fun pixelData(imageData: JsAny): JsAny = js("imageData.data")

private fun pixelWidth(imageData: JsAny): Int = js("imageData.width")

private fun pixelHeight(imageData: JsAny): Int = js("imageData.height")

private fun jsQrOptions(attemptInverted: Boolean): JsAny =
    js("({ inversionAttempts: attemptInverted ? 'attemptBoth' : 'dontInvert' })")

private fun resultText(result: JsAny): String = js("result.data")

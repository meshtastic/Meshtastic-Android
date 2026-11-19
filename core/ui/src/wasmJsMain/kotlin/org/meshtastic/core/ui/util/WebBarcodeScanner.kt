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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.camera_blocked_in_browser
import org.meshtastic.core.resources.camera_unavailable
import org.meshtastic.core.resources.qr_code_not_found
import org.meshtastic.core.resources.scan_qr_from_image
import org.meshtastic.core.ui.component.BarcodeScannerFrame
import org.meshtastic.core.ui.component.ScannerAction
import org.w3c.dom.HTMLElement
import kotlin.js.Promise
import kotlin.time.Duration.Companion.milliseconds

private val FRAME_INTERVAL = 200.milliseconds

@Composable
internal actual fun rememberPlatformBarcodeScanner(onResult: (String?) -> Unit): BarcodeScanner {
    var scanning by remember { mutableStateOf(false) }
    val currentOnResult by rememberUpdatedState(onResult)
    if (scanning) {
        WebBarcodeScannerDialog(
            onResult = {
                scanning = false
                currentOnResult(it)
            },
        )
    }
    return remember {
        object : BarcodeScanner {
            override fun startScan() {
                scanning = true
            }
        }
    }
}

// Reading a QR code from an image works in any browser, so the scanner is offered even where no camera answers.
internal actual val platformBarcodeScannerSupported: Boolean = true

private enum class ScanFailure(val message: StringResource) {
    CameraBlocked(Res.string.camera_blocked_in_browser),
    CameraUnavailable(Res.string.camera_unavailable),
    NoCodeInImage(Res.string.qr_code_not_found),
}

@Composable
private fun WebBarcodeScannerDialog(onResult: (String?) -> Unit) {
    var delivered by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ScanFailure?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // The camera loop and an image pick can both find a code; only the first answer counts.
    fun deliver(result: String?) {
        if (delivered) return
        delivered = true
        onResult(result)
    }

    val scanImage =
        ScannerAction(stringResource(Res.string.scan_qr_from_image)) {
            pickImageFile { file ->
                if (file != null) {
                    scope.launch {
                        val code = decodeQrFromImage(file)
                        if (code != null) {
                            deliver(code)
                        } else {
                            cameraReady = false
                            failure = ScanFailure.NoCodeInImage
                        }
                    }
                }
            }
        }

    BarcodeScannerFrame(
        onClose = { deliver(null) },
        showReticle = cameraReady && failure == null,
        errorMessage = failure?.let { stringResource(it.message) },
        onRetry = {
            failure = null
            attempt++
        },
        secondaryAction = scanImage,
    ) { windowModifier ->
        if (failure == null) {
            key(attempt) {
                CameraView(
                    modifier = windowModifier,
                    onReady = { cameraReady = true },
                    onFailure = {
                        cameraReady = false
                        failure = it
                    },
                    onCode = { deliver(it) },
                )
            }
        }
    }
}

/**
 * The camera as an HTML `<video>` element over the canvas, decoded every [FRAME_INTERVAL]. The element covers what
 * Compose draws beneath it, which is why it sits inside the reticle's window rather than behind the reticle.
 */
@OptIn(ExperimentalComposeUiApi::class) // HtmlElementView
@Composable
private fun CameraView(
    onReady: () -> Unit,
    onFailure: (ScanFailure) -> Unit,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnFailure by rememberUpdatedState(onFailure)
    val currentOnCode by rememberUpdatedState(onCode)
    val holder = remember { createCameraHolder() }
    val video = remember(holder) { cameraVideo(holder) }

    HtmlElementView(factory = { holder }, modifier = modifier)

    DisposableEffect(video) { onDispose { closeCamera(video) } }

    LaunchedEffect(video) {
        val error = openCamera(video).await<JsString?>()?.toString()
        if (error != null) {
            currentOnFailure(
                if (error == "NotAllowedError" || error == "SecurityError") {
                    ScanFailure.CameraBlocked
                } else {
                    ScanFailure.CameraUnavailable
                },
            )
            return@LaunchedEffect
        }
        currentOnReady()
        val decoder = QrDecoder.create()
        while (true) {
            delay(FRAME_INTERVAL)
            decoder.decode(video, cropToSquare = true)?.let {
                currentOnCode(it)
                return@LaunchedEffect
            }
        }
    }
}

private suspend fun decodeQrFromImage(file: JsAny): String? {
    val bitmap = imageBitmapOf(file).await<JsAny?>() ?: return null
    return try {
        QrDecoder.create().decode(bitmap, cropToSquare = false)
    } finally {
        closeImageBitmap(bitmap)
    }
}

private fun createCameraHolder(): HTMLElement = js(
    """{
    const holder = document.createElement('div');
    holder.style.cssText = 'width:100%;height:100%;background:#000;overflow:hidden;';
    const video = document.createElement('video');
    video.muted = true;
    video.playsInline = true;
    video.setAttribute('playsinline', '');
    video.style.cssText = 'width:100%;height:100%;object-fit:cover;display:block;';
    holder.appendChild(video);
    return holder;
}""",
)

private fun cameraVideo(holder: HTMLElement): JsAny = js("holder.firstElementChild")

// Resolves to null once the camera plays, or to the error's name. A camera that answers after the view is gone is
// closed at once, so a late grant never leaves it running.
private fun openCamera(video: JsAny): Promise<JsString?> = js(
    """navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
        .then((stream) => {
            if (video.meshtasticClosed) {
                stream.getTracks().forEach((t) => t.stop());
                return 'AbortError';
            }
            video.srcObject = stream;
            return video.play().then(() => null);
        })
        .catch((e) => (e && e.name) || 'Error')""",
)

private fun closeCamera(video: JsAny) {
    js(
        """{
        video.meshtasticClosed = true;
        if (video.srcObject) video.srcObject.getTracks().forEach((t) => t.stop());
        video.srcObject = null;
    }""",
    )
}

private fun imageBitmapOf(file: JsAny): Promise<JsAny?> = js("createImageBitmap(file).then((b) => b, () => null)")

private fun closeImageBitmap(bitmap: JsAny) {
    js("bitmap.close()")
}

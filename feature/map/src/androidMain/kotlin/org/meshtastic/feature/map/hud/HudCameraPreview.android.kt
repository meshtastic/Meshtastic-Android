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

import android.hardware.camera2.CameraCharacteristics
import android.util.Rational
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.touchlab.kermit.Logger
import kotlin.math.max
import kotlin.math.min

@Composable
@Suppress("LongMethod")
actual fun HudCameraPreview(
    onIntrinsics: (CameraIntrinsics?) -> Unit,
    onAvailable: (Boolean) -> Unit,
    onStill: (HudStill?) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnIntrinsics by rememberUpdatedState(onIntrinsics)
    val currentOnAvailable by rememberUpdatedState(onAvailable)
    val currentOnStill by rememberUpdatedState(onStill)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val displayRotation = LocalView.current.display?.rotation ?: Surface.ROTATION_0

    // Bound once the view has a size: the view port crops every stream to what the view shows.
    DisposableEffect(lifecycleOwner, viewSize, displayRotation) {
        if (viewSize.width == 0 || viewSize.height == 0) return@DisposableEffect onDispose {}
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var sensorIntrinsics: CameraIntrinsics? = null
        var boundCamera: Camera? = null
        val stateObserver =
            Observer<CameraState> { state ->
                when {
                    state.error != null -> currentOnAvailable(false)
                    state.type == CameraState.Type.OPEN -> currentOnAvailable(true)
                }
            }
        // Stabilization stays off: it crops up to a fifth of the field of view, which the intrinsics would not see.
        val preview = Preview.Builder().build()
        val imageCapture = ImageCapture.Builder().build()
        val still = HudOverlayStill(context, imageCapture)
        val group =
            UseCaseGroup.Builder()
                .setViewPort(ViewPort.Builder(Rational(viewSize.width, viewSize.height), displayRotation).build())
                .addUseCase(preview)
                .addUseCase(imageCapture)
                .addEffect(still.effect)
                .build()

        // The stream CameraX picked, and its crop, decide how much of the sensor the view shows.
        fun report() {
            val info = preview.resolutionInfo
            currentOnIntrinsics(
                sensorIntrinsics?.let { base ->
                    info?.let {
                        base.forStream(
                            streamLong = max(it.resolution.width, it.resolution.height),
                            streamShort = min(it.resolution.width, it.resolution.height),
                            cropLong = max(it.cropRect.width(), it.cropRect.height()),
                            cropShort = min(it.cropRect.width(), it.cropRect.height()),
                        )
                    } ?: base
                },
            )
        }
        preview.setSurfaceProvider { request ->
            if (disposed) {
                request.willNotProvideSurface()
            } else {
                surfaceRequest = request
                report()
            }
        }

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (disposed) return@addListener
                @Suppress("TooGenericExceptionCaught")
                try {
                    val bound = future.get()
                    provider = bound
                    val camera = bound.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                    sensorIntrinsics = camera.intrinsics()
                    report()
                    currentOnStill(still)
                    // Another app holding the camera, or a HAL fault, arrives here rather than as a bind failure.
                    camera.cameraInfo.cameraState.observe(lifecycleOwner, stateObserver)
                    boundCamera = camera
                } catch (e: Exception) {
                    Logger.w(e) { "Mesh view camera bind failed" }
                    currentOnIntrinsics(null)
                    currentOnAvailable(false)
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            disposed = true
            currentOnStill(null)
            boundCamera?.cameraInfo?.cameraState?.removeObserver(stateObserver)
            surfaceRequest?.willNotProvideSurface()
            surfaceRequest = null
            provider?.unbind(preview, imageCapture)
            still.close()
        }
    }

    // Crop, not fit: CameraIntrinsics.focalPx assumes the preview fills the view and overflows the long side.
    Box(modifier = modifier.onSizeChanged { viewSize = it }) {
        surfaceRequest?.let {
            CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** Focal length and the active sensor area in millimeters, or null when the camera does not report them. */
private fun Camera.intrinsics(): CameraIntrinsics? {
    val info = Camera2Interop.getCameraCharacteristics(cameraInfo)
    val focal = info.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    val physical = info.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    val pixels = info.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
    val active = info.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
    if (focal == null || physical == null || pixels == null || active == null) return null
    // The physical size covers the whole pixel array; the preview streams only the active area.
    val width = physical.width * active.width() / pixels.width
    val height = physical.height * active.height() / pixels.height
    // A calibrated focal length, in pre-correction active-array pixels, beats the nominal one when the camera has it:
    // on a logical multi-camera the first listed focal length need not be the lens in use.
    val calibratedFx = info.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)?.firstOrNull()
    // Pixels to millimeters by the sensor's pixel pitch.
    val nominalOrCalibrated = calibratedFx?.takeIf { it > 0f }?.let { it * physical.width / pixels.width } ?: focal
    val zoom = cameraInfo.zoomState.value?.zoomRatio ?: 1f
    return CameraIntrinsics(
        focalLengthMm = nominalOrCalibrated * zoom,
        sensorLongMm = max(width, height),
        sensorShortMm = min(width, height),
    )
}

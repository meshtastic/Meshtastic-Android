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
package org.meshtastic.feature.node.component

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.android.filament.Engine
import io.github.sceneview.SceneScope
import io.github.sceneview.SceneView
import io.github.sceneview.createDefaultCameraManipulator
import io.github.sceneview.environment.Environment
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.model.model
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.repository.MirrorFormat
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.feature.node.model.GlbBounds
import java.nio.ByteBuffer
import io.github.sceneview.node.ImageNode as ImageNodeImpl

internal actual val deviceModelDrawsScreen: Boolean = true

/**
 * SceneView directly rather than the multiplatform SceneViewer, whose Android HDR path swaps out and frees its
 * placeholder environment while the scene still renders it, a native crash. The probe loads before the scene exists, so
 * nothing is swapped. SceneView centers the model on the origin, so the orbit targets the origin.
 */
@Composable
internal actual fun DeviceModelScene(
    glb: ByteArray,
    bounds: GlbBounds,
    onError: () -> Unit,
    modifier: Modifier,
    visible: Boolean,
    screen: GlbBounds?,
    screenFrame: MirrorFrame?,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val currentOnError by rememberUpdatedState(onError)
    val environment by
        produceState<Environment?>(null, environmentLoader) {
            value =
                safeCatching { environmentLoader.loadHDREnvironment(STUDIO_LIGHT_PROBE, createSkybox = false) }
                    .getOrNull()
                    .also { if (it == null) currentOnError() }
        }
    // Captured, not read through the delegate: the null effect's dispose runs after the state already holds the load.
    val loadedEnvironment = environment
    DisposableEffect(loadedEnvironment) { onDispose { loadedEnvironment?.let(environmentLoader::destroyEnvironment) } }
    val cameraManipulator =
        remember(bounds) {
            createDefaultCameraManipulator(eyePosition = orbitEye(bounds), targetPosition = Position(0f))
        }
    // Hidden, only the scene goes: the engine, its compiled materials, the probe and the camera stay for when it shows
    // again.
    if (visible && loadedEnvironment != null) {
        OrbitScene(
            glb,
            engine,
            modelLoader,
            environmentLoader,
            loadedEnvironment,
            cameraManipulator,
            onError,
            modifier,
        ) {
            if (screen != null && screenFrame != null) DeviceScreenNode(screen.min, screen.max, screenFrame)
        }
    }
}

/**
 * The model instance does not survive its scene's teardown, so it is rebuilt from the bytes each time, which is cheap.
 */
@Composable
@Suppress("LongParameterList")
private fun OrbitScene(
    glb: ByteArray,
    engine: Engine,
    modelLoader: ModelLoader,
    environmentLoader: EnvironmentLoader,
    environment: Environment,
    cameraManipulator: CameraGestureDetector.CameraManipulator,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable SceneScope.() -> Unit = {},
) {
    val currentOnError by rememberUpdatedState(onError)
    val instance =
        remember(modelLoader, glb) {
            runCatching { modelLoader.createModelInstance(ByteBuffer.wrap(glb)) }.getOrNull()
        }
    DisposableEffect(instance) { onDispose { instance?.let { modelLoader.destroyModel(it.model) } } }
    SideEffect { if (instance == null) currentOnError() }
    if (instance == null) return
    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        environment = environment,
        isOpaque = false,
        cameraManipulator = cameraManipulator,
    ) {
        ModelNode(modelInstance = instance)
        overlay()
    }
}

/**
 * The frame on a plane just proud of the display glass, facing +Z like the flasher's models. The node is created once
 * and fed new bitmaps: replacing it would make SceneView re-center the whole scene on every frame.
 */
@Composable
fun SceneScope.DeviceScreenNode(screenMin: FloatArray, screenMax: FloatArray, frame: MirrorFrame) {
    val screen = GlbBounds(screenMin, screenMax)
    val bitmap = remember(frame) { frame.toBitmap() } ?: return
    var node by remember { mutableStateOf<ImageNodeImpl?>(null) }
    val first = remember { bitmap }
    ImageNode(
        bitmap = first,
        size = Size(screen.max[0] - screen.min[0], screen.max[1] - screen.min[1]),
        center = Position(screen.center[0], screen.center[1], screen.max[2] + SCREEN_LIFT_METERS),
        normal = Direction(0f, 0f, 1f),
        apply = { node = this },
    )
    // A swapped texture is invisible to SceneView's on-demand loop until something asks for a frame.
    SideEffect {
        node
            ?.takeIf { it.bitmap !== bitmap }
            ?.let {
                it.bitmap = bitmap
                it.requestRender()
            }
    }
}

private const val SCREEN_LIFT_METERS = 0.0001f
private const val PIXELS_PER_PAGE = 8
private const val PIXEL_ON = 0xFFE8F4FF.toInt()
private const val PIXEL_OFF = 0xFF05070A.toInt()

/** MONO_VLSB only: byte `(y / 8) * width + x`, bit `y % 8`. */
private fun MirrorFrame.toBitmap(): Bitmap? {
    if (format != MirrorFormat.MONO_VLSB || pixels.size < (height + PIXELS_PER_PAGE - 1) / PIXELS_PER_PAGE * width) {
        return null
    }
    val argb =
        IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val set = pixels[(y / PIXELS_PER_PAGE) * width + x].toInt() and (1 shl (y % PIXELS_PER_PAGE)) != 0
            if (set) PIXEL_ON else PIXEL_OFF
        }
    return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
}

private fun orbitEye(bounds: GlbBounds): Position {
    val eye = orbitEyeOffset(bounds)
    return Position(eye[0], eye[1], eye[2])
}

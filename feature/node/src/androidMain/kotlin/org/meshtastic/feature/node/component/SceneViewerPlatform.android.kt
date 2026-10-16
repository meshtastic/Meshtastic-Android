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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.createDefaultCameraManipulator
import io.github.sceneview.environment.Environment
import io.github.sceneview.math.Position
import io.github.sceneview.model.model
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.feature.node.model.GlbBounds
import java.nio.ByteBuffer

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
    val instance =
        remember(modelLoader, glb) {
            runCatching { modelLoader.createModelInstance(ByteBuffer.wrap(glb)) }.getOrNull()
        }
    DisposableEffect(instance) { onDispose { instance?.let { modelLoader.destroyModel(it.model) } } }
    SideEffect { if (instance == null) currentOnError() }
    // Hidden, only the surface goes: the engine, probe and model stay loaded for when it shows again.
    if (instance == null || !visible) return
    val loaded = loadedEnvironment ?: return
    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        environment = loaded,
        isOpaque = false,
        cameraManipulator =
        remember(bounds) {
            createDefaultCameraManipulator(eyePosition = orbitEye(bounds), targetPosition = Position(0f))
        },
    ) {
        ModelNode(modelInstance = instance)
    }
}

private fun orbitEye(bounds: GlbBounds): Position {
    val eye = orbitEyeOffset(bounds)
    return Position(eye[0], eye[1], eye[2])
}

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
import androidx.compose.ui.Modifier
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.compose.EnvironmentSource
import io.github.sceneview.compose.Lighting
import io.github.sceneview.compose.ModelSource
import io.github.sceneview.compose.SceneViewer
import io.github.sceneview.compose.rememberCameraState
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.feature.node.model.GlbBounds
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A CC0 studio light probe (Poly Haven, Studio Small 09), at the path both renderers resolve a bundled file by. */
internal const val STUDIO_LIGHT_PROBE =
    "composeResources/org.meshtastic.core.resources/files/environments/studio_small_09.hdr"

private const val CAMERA_DISTANCE_PER_EXTENT = 2.2f
private const val CAMERA_AZIMUTH_DEGREES = 30f
private const val CAMERA_ELEVATION_DEGREES = 20f
private const val SUN_LUX = 40_000f
private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

/** The camera's offset from the model's center: the same three-quarter view on every platform. */
internal fun orbitEyeOffset(bounds: GlbBounds): FloatArray {
    val distance = bounds.extent * CAMERA_DISTANCE_PER_EXTENT
    val azimuth = CAMERA_AZIMUTH_DEGREES * DEGREES_TO_RADIANS
    val elevation = CAMERA_ELEVATION_DEGREES * DEGREES_TO_RADIANS
    val horizontal = distance * cos(elevation)
    return floatArrayOf(horizontal * sin(azimuth), distance * sin(elevation), horizontal * cos(azimuth))
}

/** Whether this platform's [DeviceModelScene] draws [MirrorFrame]s on the glass; where it does not, nothing arms. */
internal expect val deviceModelDrawsScreen: Boolean

/**
 * The orbitable model, lit by the studio probe over a transparent backdrop so the theme shows behind it. When both
 * [screen] and [screenFrame] are present, the frame is drawn on the model's display glass.
 */
@Composable
internal expect fun DeviceModelScene(
    glb: ByteArray,
    bounds: GlbBounds,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    screen: GlbBounds? = null,
    screenFrame: MirrorFrame? = null,
)

/**
 * The multiplatform SceneViewer, which places the model at its authored coordinates on desktop: the orbit target is the
 * bounds center and the distance a multiple of the longest side.
 */
@Composable
internal fun SceneViewerModelScene(
    glb: ByteArray,
    bounds: GlbBounds,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val center = bounds.center
    val camera =
        rememberCameraState(
            target = Float3(center[0], center[1], center[2]),
            distance = bounds.extent * CAMERA_DISTANCE_PER_EXTENT,
            azimuth = CAMERA_AZIMUTH_DEGREES,
            elevation = CAMERA_ELEVATION_DEGREES,
        )
    SceneViewer(
        model = ModelSource.Bytes(glb),
        modifier = modifier,
        camera = camera,
        lighting = Lighting(intensity = SUN_LUX),
        environment = EnvironmentSource.Hdr(STUDIO_LIGHT_PROBE, showSkybox = false),
        onError = { onError() },
    )
}

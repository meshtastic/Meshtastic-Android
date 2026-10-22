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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.layer.GraphicsLayer
import kotlin.math.max

/**
 * Saves a picture of the mesh view from [still], with [overlay] recorded at the shutter drawn over it; null where the
 * platform cannot or before the camera is bound.
 */
@Composable expect fun rememberHudCapture(still: HudStill?, overlay: GraphicsLayer): (() -> Unit)?

/**
 * The affine map, as a row-major 3x3, from view pixels to the pixels of a camera buffer whose [crop] the view shows
 * rotated clockwise by [rotationDegrees] and center-cropped to fill, the way the preview fills the mesh view.
 */
@Suppress("LongParameterList")
fun viewToBufferMatrix(
    viewWidth: Float,
    viewHeight: Float,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    rotationDegrees: Int,
): FloatArray {
    val (cos, sin) =
        when (((rotationDegrees % FULL_TURN) + FULL_TURN) % FULL_TURN) {
            QUARTER_TURN -> 0f to 1f
            HALF_TURN -> -1f to 0f
            THREE_QUARTER_TURN -> 0f to -1f
            else -> 1f to 0f
        }
    val cropWidth = cropRight - cropLeft
    val cropHeight = cropBottom - cropTop
    val quarter = sin != 0f
    val uprightWidth = if (quarter) cropHeight else cropWidth
    val uprightHeight = if (quarter) cropWidth else cropHeight
    // View pixels per buffer pixel: the upright crop fills the view and overflows its long side.
    val scale = max(viewWidth / uprightWidth, viewHeight / uprightHeight)
    val viewCenterX = viewWidth / 2
    val viewCenterY = viewHeight / 2
    val cropCenterX = (cropLeft + cropRight) / 2
    val cropCenterY = (cropTop + cropBottom) / 2
    // Undo the upright rotation about the crop's center, then scale view pixels down to buffer pixels.
    return floatArrayOf(
        cos / scale,
        sin / scale,
        cropCenterX - (cos * viewCenterX + sin * viewCenterY) / scale,
        -sin / scale,
        cos / scale,
        cropCenterY - (-sin * viewCenterX + cos * viewCenterY) / scale,
        0f,
        0f,
        1f,
    )
}

private const val QUARTER_TURN = 90
private const val HALF_TURN = 180
private const val THREE_QUARTER_TURN = 270
private const val FULL_TURN = 360

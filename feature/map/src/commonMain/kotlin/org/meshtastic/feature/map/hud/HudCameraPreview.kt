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
import androidx.compose.ui.Modifier
import kotlin.math.max
import kotlin.math.tan

/** Fallback when the camera reports no intrinsics: a typical phone main camera's horizontal field of view. */
private const val FALLBACK_HORIZONTAL_FOV_DEG = 66.0

/** The back camera's focal length and the size of the sensor area the preview shows, both in millimeters. */
data class CameraIntrinsics(val focalLengthMm: Float, val sensorLongMm: Float, val sensorShortMm: Float) {
    /**
     * The sensor area a preview stream of [streamLong] by [streamShort] pixels shows after its [cropLong] by
     * [cropShort] crop. The camera fills a stream whose aspect differs from the sensor's by cropping the sensor to that
     * aspect about its center, so a 16:9 stream from a 4:3 sensor loses sensor height before any crop is applied.
     */
    fun forStream(streamLong: Int, streamShort: Int, cropLong: Int, cropShort: Int): CameraIntrinsics {
        if (streamLong <= 0 || streamShort <= 0) return this
        val streamAspect = streamLong.toFloat() / streamShort
        val (coveredLong, coveredShort) =
            if (streamAspect >= sensorLongMm / sensorShortMm) {
                sensorLongMm to sensorLongMm / streamAspect
            } else {
                sensorShortMm * streamAspect to sensorShortMm
            }
        return copy(
            sensorLongMm = coveredLong * cropLong.coerceIn(1, streamLong) / streamLong,
            sensorShortMm = coveredShort * cropShort.coerceIn(1, streamShort) / streamShort,
        )
    }

    /**
     * Focal length in view pixels. The preview fills the view and crops the overflow, so the scale is the larger of the
     * two axis ratios; the sensor's long side lies along the view's long side whatever the display rotation.
     */
    fun focalPx(viewWidth: Float, viewHeight: Float): Float {
        val viewLong = max(viewWidth, viewHeight)
        val viewShort = minOf(viewWidth, viewHeight)
        return focalLengthMm * max(viewLong / sensorLongMm, viewShort / sensorShortMm)
    }
}

fun fallbackFocalPx(viewWidth: Float, viewHeight: Float): Float =
    (max(viewWidth, viewHeight) / 2 / tan(FALLBACK_HORIZONTAL_FOV_DEG.toRadians() / 2)).toFloat()

/**
 * The back camera's live preview, filling [modifier]; reports intrinsics once the camera is bound, and [onAvailable]
 * false when it cannot bind or the camera fails, true once it opens. [onStill] gets the bound camera's still, or null
 * once it is released or where there is none.
 */
@Composable
expect fun HudCameraPreview(
    onIntrinsics: (CameraIntrinsics?) -> Unit,
    onAvailable: (Boolean) -> Unit,
    onStill: (HudStill?) -> Unit,
    modifier: Modifier = Modifier,
)

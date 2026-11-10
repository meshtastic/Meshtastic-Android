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

import android.hardware.SensorManager
import android.view.Surface

internal const val ROTATION_MATRIX_SIZE = 9

/**
 * The camera pose for a device-to-east-north-up [attitude] quaternion in (x, y, z, w) order, which both the rotation
 * vector and the Fused Orientation Provider report. [matrix] is scratch space of [ROTATION_MATRIX_SIZE].
 */
internal fun attitudeToCameraPose(
    attitude: FloatArray,
    displayRotation: Int,
    matrix: FloatArray = FloatArray(ROTATION_MATRIX_SIZE),
): CameraPose {
    SensorManager.getRotationMatrixFromVector(matrix, attitude)
    return matrix.toCameraPose(displayRotation)
}

/**
 * The matrix maps device coordinates into east-north-up, so its columns are the device axes in that frame. The back
 * camera looks down device -Z, and which device axis is the screen's right depends on the display rotation.
 */
@Suppress("MagicNumber")
private fun FloatArray.toCameraPose(displayRotation: Int): CameraPose {
    val deviceX = Vec3(this[0].toDouble(), this[3].toDouble(), this[6].toDouble())
    val deviceY = Vec3(this[1].toDouble(), this[4].toDouble(), this[7].toDouble())
    val forward = Vec3(-this[2].toDouble(), -this[5].toDouble(), -this[8].toDouble())
    val (right, up) =
        when (displayRotation) {
            Surface.ROTATION_90 -> -deviceY to deviceX
            Surface.ROTATION_180 -> -deviceX to -deviceY
            Surface.ROTATION_270 -> deviceY to -deviceX
            else -> deviceX to deviceY
        }
    return CameraPose(right = right, up = up, forward = forward)
}

private operator fun Vec3.unaryMinus() = Vec3(-x, -y, -z)

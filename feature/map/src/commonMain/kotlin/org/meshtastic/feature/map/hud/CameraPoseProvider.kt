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

import kotlinx.coroutines.flow.Flow

/**
 * [calibration] is the platform's sensor status, 3 high down to 0 unreliable, and -1 before the first reading.
 * [headingAccuracyDeg] is the sensor's own estimate of its heading error, null when it gives none. [trueNorth] is true
 * when the source already applied the declination, so the pose is referenced to true north rather than magnetic.
 * [thermal] is how hot the phone runs, so the view can shed work before the system throttles it.
 */
data class CameraPoseState(
    val pose: CameraPose? = null,
    val hasSensor: Boolean = true,
    val calibration: Int = CALIBRATION_UNKNOWN,
    val headingAccuracyDeg: Double? = null,
    val trueNorth: Boolean = false,
    val thermal: HudThermal = HudThermal.NORMAL,
)

/** [HOT] slows the sensors and says so; [CRITICAL] also releases the camera, leaving the overlay as a compass. */
enum class HudThermal {
    NORMAL,
    HOT,
    CRITICAL,
}

const val CALIBRATION_UNKNOWN = -1

interface CameraPoseProvider {
    fun poseUpdates(): Flow<CameraPoseState>
}

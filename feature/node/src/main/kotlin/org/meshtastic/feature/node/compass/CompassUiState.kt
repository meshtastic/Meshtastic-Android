/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.node.compass

import androidx.compose.ui.graphics.Color
import org.meshtastic.proto.Config

private const val DEFAULT_TARGET_COLOR_HEX = 0xFFFF9800

enum class CompassWarning {
    NO_MAGNETOMETER,
    NO_LOCATION_PERMISSION,
    LOCATION_DISABLED,
    NO_LOCATION_FIX,
}

/** Render-ready state for the compass sheet (heading, bearing, distances, and warnings). */
data class CompassUiState(
    val targetName: String = "",
    val targetColor: Color = Color(DEFAULT_TARGET_COLOR_HEX),
    val heading: Float? = null,
    val bearing: Float? = null,
    val distanceText: String? = null,
    val bearingText: String? = null,
    val lastUpdateText: String? = null,
    val positionTimeSec: Long? = null, // Epoch seconds for the target position (used for elapsed display)
    val warnings: List<CompassWarning> = emptyList(),
    val errorRadiusText: String? = null,
    val angularErrorDeg: Float? = null,
    val isAligned: Boolean = false,
    val hasTargetPosition: Boolean = true,
    val displayUnits: Config.DisplayConfig.DisplayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
    val targetAltitude: Int? = null,
)

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
package org.meshtastic.core.model.geofence

import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import kotlin.math.abs

/**
 * Radius presets for the waypoint editor. The wire value is ALWAYS metres; the UI renders each value locale-aware via
 * `DistanceExtensions`. `0` means "Off" (no circle). Imperial values are chosen to render as clean feet/mile figures.
 * Do NOT copy Apple's imperial-only table — the selector list is locale-driven by [forUnits].
 */
@Suppress("MagicNumber")
object GeofenceRadiusPresets {
    /** Off, 100 m, 250 m, 500 m, 1 km, 2 km, 5 km. */
    val METRIC_METERS: List<Int> = listOf(0, 100, 250, 500, 1000, 2000, 5000)

    /** Off, ~250 ft, ~500 ft, ~1000 ft, 1 mi, 2 mi, 5 mi (stored as metres). */
    val IMPERIAL_METERS: List<Int> = listOf(0, 76, 152, 305, 1609, 3219, 8047)

    fun forUnits(units: DisplayUnits): List<Int> =
        if (units == DisplayUnits.IMPERIAL) IMPERIAL_METERS else METRIC_METERS

    /** The preset (in the active unit system) closest to [meters] — used to highlight the current selection. */
    fun nearest(meters: Int, units: DisplayUnits): Int = forUnits(units).minBy { abs(it - meters) }
}

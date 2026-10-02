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
package org.meshtastic.feature.map

import org.meshtastic.core.common.util.latLongToMeter
import org.meshtastic.proto.Position

/** Wider than a stationary fix's GPS jitter, narrower than a walked block. */
const val TRACK_STOP_RADIUS_METERS = 25.0

private const val DEG_PER_UNIT = 1e-7

/** Consecutive fixes drawn as one point: the newest fix of the run, and the time the run began. */
data class TrackRun(val position: Position, val firstTime: Int) {
    fun covers(time: Int): Boolean = time in firstTime..position.time
}

/**
 * Collapses each run of consecutive fixes that stays within [radiusMeters] of the run's first fix into one [TrackRun].
 * [positions] must be oldest-first, and so is the result.
 *
 * Distance is measured from the run's first fix, not the previous one, so a slow drift still breaks into new points. A
 * fix missing either ordinate is never merged.
 */
fun mergeStationaryRuns(positions: List<Position>, radiusMeters: Double = TRACK_STOP_RADIUS_METERS): List<TrackRun> {
    val runs = ArrayList<TrackRun>(positions.size)
    var runStart: Position? = null
    for (position in positions) {
        val start = runStart
        if (start != null && start.isWithin(position, radiusMeters)) {
            runs[runs.lastIndex] = runs.last().copy(position = position)
        } else {
            runs += TrackRun(position = position, firstTime = position.time)
            runStart = position
        }
    }
    return runs
}

private fun Position.isWithin(other: Position, radiusMeters: Double): Boolean {
    val latA = latitude_i
    val lonA = longitude_i
    val latB = other.latitude_i
    val lonB = other.longitude_i
    return latA != null &&
        lonA != null &&
        latB != null &&
        lonB != null &&
        latLongToMeter(latA * DEG_PER_UNIT, lonA * DEG_PER_UNIT, latB * DEG_PER_UNIT, lonB * DEG_PER_UNIT) <=
        radiusMeters
}

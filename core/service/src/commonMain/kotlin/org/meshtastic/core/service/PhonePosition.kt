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
package org.meshtastic.core.service

import org.meshtastic.core.model.Position
import org.meshtastic.core.model.util.GeoConstants
import org.meshtastic.core.model.util.UnitConversions
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import org.meshtastic.proto.Position as ProtoPosition

/**
 * The position the phone reports for its node. A reading the fix lacks stays unset rather than going out as 0, and
 * speed and track use the units firmware's own GPS sends: km/h and 1e-5 degrees.
 */
internal fun phonePosition(
    latitude: Double,
    longitude: Double,
    timeMillis: Long,
    mslAltitudeMeters: Double?,
    haeAltitudeMeters: Double?,
    speedMetersPerSecond: Float?,
    bearingDegrees: Float?,
): ProtoPosition = ProtoPosition.Builder()
    .also { wb ->
        wb.latitude_i = Position.degI(latitude)
        wb.longitude_i = Position.degI(longitude)
        wb.altitude = mslAltitudeMeters?.toInt()
        wb.altitude_hae = haeAltitudeMeters?.toInt()
        wb.time = timeMillis.milliseconds.inWholeSeconds.toInt()
        wb.ground_speed = speedMetersPerSecond?.let { UnitConversions.metersPerSecondToKph(it).roundToInt() }
        wb.ground_track = bearingDegrees?.let { (it / GeoConstants.HEADING_DEG).roundToInt() }
        wb.location_source = ProtoPosition.LocSource.LOC_EXTERNAL
    }
    .build()

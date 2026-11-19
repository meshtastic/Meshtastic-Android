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
import org.meshtastic.core.repository.GnssFix
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import org.meshtastic.proto.Position as ProtoPosition

/**
 * The position the phone reports for its node. A reading the fix lacks stays unset rather than going out as 0, and
 * speed and track use the units firmware's own GPS sends: km/h and 1e-5 degrees. Satellites and dilution of precision
 * come from the receiver's NMEA when it gave some; firmware keeps the whole position and broadcasts them as
 * `position_flags` allows.
 */
internal fun phonePosition(
    latitude: Double,
    longitude: Double,
    timeMillis: Long,
    mslAltitudeMeters: Double?,
    haeAltitudeMeters: Double?,
    speedMetersPerSecond: Float?,
    bearingDegrees: Float?,
    gnss: GnssFix? = null,
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
        // These are not optional on the wire, where 0 is "not reported", which is what an unset reading means.
        gnss?.let { fix ->
            wb.sats_in_view = fix.satellitesUsed ?: 0
            wb.HDOP = fix.hdop ?: 0
            wb.PDOP = fix.pdop ?: 0
            wb.VDOP = fix.vdop ?: 0
            wb.fix_quality = fix.fixQuality ?: 0
            wb.fix_type = fix.fixType ?: 0
        }
    }
    .build()

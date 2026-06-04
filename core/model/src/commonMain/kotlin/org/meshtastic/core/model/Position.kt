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
package org.meshtastic.core.model

import org.meshtastic.core.common.util.bearing
import org.meshtastic.core.common.util.latLongToMeter
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.util.anonymize

data class Position(
    val latitude: Double,
    val longitude: Double,
    val altitude: Int,
    val time: Int = currentTime(), // default to current time in secs (NOT MILLISECONDS!)
    val satellitesInView: Int = 0,
    val groundSpeed: Int = 0,
    val groundTrack: Int = 0, // "heading"
    val precisionBits: Int = 0,
) {

    @Suppress("MagicNumber")
    companion object {
        fun degD(i: Int) = i * 1e-7

        fun degI(d: Double) = (d * 1e7).toInt()

        fun currentTime() = nowSeconds.toInt()
    }

    /**
     * Create our model object from a protobuf. If time is unspecified in the protobuf, the provided default time will
     * be used.
     */
    constructor(
        position: org.meshtastic.proto.Position,
        defaultTime: Int = currentTime(),
    ) : this(
        degD(position.latitude_i ?: 0),
        degD(position.longitude_i ?: 0),
        position.altitude ?: 0,
        if (position.time != 0) position.time else defaultTime,
        position.sats_in_view,
        position.ground_speed ?: 0,
        position.ground_track ?: 0,
        position.precision_bits,
    )

    /** @return distance in meters to some other position */
    fun distance(o: Position) = latLongToMeter(latitude, longitude, o.latitude, o.longitude)

    /** @return bearing to the other position in degrees */
    fun bearing(o: Position) = bearing(latitude, longitude, o.latitude, o.longitude)

    @Suppress("MagicNumber")
    fun isValid(): Boolean = latitude != 0.0 &&
        longitude != 0.0 &&
        (latitude >= -90 && latitude <= 90.0) &&
        (longitude >= -180 && longitude <= 180)

    override fun toString(): String =
        "Position(lat=${latitude.anonymize}, lon=${longitude.anonymize}, alt=${altitude.anonymize}, time=$time)"
}

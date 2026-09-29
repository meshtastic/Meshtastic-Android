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
package org.meshtastic.core.repository

/**
 * One location fix from the device. A reading the platform did not report is null, never zero: zero is a real altitude,
 * speed and bearing.
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    /** Height above the WGS84 ellipsoid. */
    val altitudeMeters: Double? = null,
    /** Height above mean sea level, where the platform could derive it. */
    val mslAltitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val bearingDegrees: Float? = null,
    /** UTC epoch milliseconds. */
    val timeMillis: Long,
)

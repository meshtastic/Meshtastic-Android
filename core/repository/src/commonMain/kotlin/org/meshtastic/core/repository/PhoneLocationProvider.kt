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

import kotlinx.coroutines.flow.Flow

/**
 * [altitude] is height above the WGS84 ellipsoid, as Android reports it, and null when the fix has none (a network
 * fix). [mslAltitude] is height above mean sea level, null when the platform cannot convert. [accuracyM] and
 * [verticalAccuracyM] are the fix's accuracy radii, null when the platform reports none. [speedMps] is ground speed,
 * null when unknown.
 */
data class PhoneLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val timeMillis: Long,
    val mslAltitude: Double? = null,
    val accuracyM: Float? = null,
    val verticalAccuracyM: Float? = null,
    val speedMps: Float? = null,
)

data class PhoneLocationState(
    val permissionGranted: Boolean,
    val providerEnabled: Boolean,
    val location: PhoneLocation? = null,
) {
    val hasFix: Boolean
        get() = location != null
}

interface PhoneLocationProvider {
    fun locationUpdates(): Flow<PhoneLocationState>
}

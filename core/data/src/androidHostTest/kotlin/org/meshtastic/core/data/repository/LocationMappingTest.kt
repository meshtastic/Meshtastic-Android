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
package org.meshtastic.core.data.repository

import androidx.core.location.LocationCompat
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.repository.Location
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import android.location.Location as AndroidLocation

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationMappingTest {

    private fun fix(configure: AndroidLocation.() -> Unit = {}) = AndroidLocation("gps").apply {
        latitude = 39.7392
        longitude = -104.9903
        time = 1_700_000_000_000L
        configure()
    }

    @Test
    fun `readings the fix does not carry map to null`() {
        assertEquals(
            Location(latitude = 39.7392, longitude = -104.9903, timeMillis = 1_700_000_000_000L),
            fix().toLocation(),
        )
    }

    @Test
    fun `zero readings the fix does carry stay zero`() {
        val location =
            fix {
                altitude = 0.0
                mslAltitudeMeters = 0.0
                accuracy = 0f
                speed = 0f
                bearing = 0f
            }
                .toLocation()

        assertEquals(
            Location(
                latitude = 39.7392,
                longitude = -104.9903,
                altitudeMeters = 0.0,
                mslAltitudeMeters = 0.0,
                accuracyMeters = 0f,
                speedMetersPerSecond = 0f,
                bearingDegrees = 0f,
                timeMillis = 1_700_000_000_000L,
            ),
            location,
        )
    }

    @Test
    fun `reported readings carry through unchanged`() {
        val location =
            fix {
                altitude = 1650.5
                mslAltitudeMeters = 1633.25
                accuracy = 4.5f
                speed = 12.25f
                bearing = 271.5f
            }
                .toLocation()

        assertEquals(
            Location(
                latitude = 39.7392,
                longitude = -104.9903,
                altitudeMeters = 1650.5,
                mslAltitudeMeters = 1633.25,
                accuracyMeters = 4.5f,
                speedMetersPerSecond = 12.25f,
                bearingDegrees = 271.5f,
                timeMillis = 1_700_000_000_000L,
            ),
            location,
        )
    }

    @Test
    @Config(sdk = [33])
    fun `below API 34 the compat MSL altitude still carries through`() {
        val fix = fix()
        LocationCompat.setMslAltitudeMeters(fix, 1633.25)

        assertEquals(1633.25, fix.toLocation().mslAltitudeMeters)
    }
}

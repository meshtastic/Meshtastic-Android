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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.meshtastic.proto.Position as ProtoPosition

class PhonePositionTest {

    private fun fix(
        mslAltitudeMeters: Double? = null,
        haeAltitudeMeters: Double? = null,
        speedMetersPerSecond: Float? = null,
        bearingDegrees: Float? = null,
    ) = phonePosition(
        latitude = 1.5,
        longitude = -2.25,
        timeMillis = 1_700_000_000_500,
        mslAltitudeMeters = mslAltitudeMeters,
        haeAltitudeMeters = haeAltitudeMeters,
        speedMetersPerSecond = speedMetersPerSecond,
        bearingDegrees = bearingDegrees,
    )

    @Test
    fun `readings the fix lacks stay unset`() {
        val position = fix()

        assertNull(position.altitude)
        assertNull(position.altitude_hae)
        assertNull(position.ground_speed)
        assertNull(position.ground_track)
    }

    @Test
    fun `zero readings are sent as zero`() {
        val position =
            fix(mslAltitudeMeters = 0.0, haeAltitudeMeters = 0.0, speedMetersPerSecond = 0f, bearingDegrees = 0f)

        assertEquals(0, position.altitude)
        assertEquals(0, position.altitude_hae)
        assertEquals(0, position.ground_speed)
        assertEquals(0, position.ground_track)
    }

    @Test
    fun `speed goes out in km per hour`() {
        assertEquals(36, fix(speedMetersPerSecond = 10f).ground_speed)
        assertEquals(5, fix(speedMetersPerSecond = 1.4f).ground_speed)
    }

    @Test
    fun `track goes out in hundred-thousandths of a degree`() {
        assertEquals(9_000_000, fix(bearingDegrees = 90f).ground_track)
        assertEquals(35_950_000, fix(bearingDegrees = 359.5f).ground_track)
    }

    @Test
    fun `each altitude keeps its own datum`() {
        val position = fix(mslAltitudeMeters = 12.9, haeAltitudeMeters = -20.4)

        assertEquals(12, position.altitude)
        assertEquals(-20, position.altitude_hae)
    }

    @Test
    fun `coordinates and time use the wire scale`() {
        val position = fix()

        assertEquals(15_000_000, position.latitude_i)
        assertEquals(-22_500_000, position.longitude_i)
        assertEquals(1_700_000_000, position.time)
        assertEquals(ProtoPosition.LocSource.LOC_EXTERNAL, position.location_source)
    }
}

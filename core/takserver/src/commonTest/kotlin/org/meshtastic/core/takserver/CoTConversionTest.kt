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
package org.meshtastic.core.takserver

import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CoTConversionTest {

    @Test
    fun testPositionToCoTMessage() {
        val position =
            Position(
                latitude_i = 377749000,
                longitude_i = -1224194000,
                altitude = 15,
                ground_speed = 5,
                ground_track = 180,
                time = 1620000000,
            )

        val cot =
            position.toCoTMessage(uid = "!12345678", callsign = "TestUser", team = "Red", role = "HQ", battery = 85)

        assertEquals("a-f-G-U-C", cot.type)
        assertEquals("!12345678", cot.uid)

        assertEquals(37.7749, cot.latitude, 0.0001)
        assertEquals(-122.4194, cot.longitude, 0.0001)
        assertEquals(15.0, cot.hae, 0.0001)

        val track = cot.track
        assertNotNull(track)
        assertEquals(5.0, track.speed, 0.0001)
        assertEquals(180.0, track.course, 0.0001)

        assertEquals("TestUser", cot.contact?.callsign)
        assertEquals("Red", cot.group?.name)
        assertEquals("HQ", cot.group?.role)
        assertEquals(85, cot.status?.battery)
    }

    @Test
    fun testUserToCoTMessage() {
        val user =
            User(
                id = "!87654321",
                long_name = "LongName",
                short_name = "SN",
                macaddr = "00:11:22:33:44:55".encodeUtf8(),
            )

        val cot = user.toCoTMessage(position = null, team = "Blue", role = "Sniper", battery = 92)

        assertEquals("a-f-G-U-C", cot.type)
        assertEquals("!87654321", cot.uid)

        assertEquals(0.0, cot.latitude, 0.0001)
        assertEquals(0.0, cot.longitude, 0.0001)

        assertEquals("SN", cot.contact?.callsign)
        assertEquals("Blue", cot.group?.name)
        assertEquals("Sniper", cot.group?.role)
        assertEquals(92, cot.status?.battery)
    }
}

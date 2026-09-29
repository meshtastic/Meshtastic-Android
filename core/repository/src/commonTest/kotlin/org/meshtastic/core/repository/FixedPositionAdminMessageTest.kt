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

import org.meshtastic.core.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.meshtastic.proto.Position as ProtoPosition

class FixedPositionAdminMessageTest {

    private fun protoPosition(altitude: Int?) = ProtoPosition.Builder()
        .also { wb ->
            wb.latitude_i = 525_200_000
            wb.longitude_i = 134_050_000
            wb.altitude = altitude
        }
        .build()

    @Test
    fun `a fixed position without altitude is sent without one`() {
        val message = Position(protoPosition(altitude = null)).toFixedPositionAdminMessage()

        assertNull(assertNotNull(message.set_fixed_position).altitude)
    }

    @Test
    fun `a sea level fixed position is sent as zero`() {
        val message = Position(protoPosition(altitude = 0)).toFixedPositionAdminMessage()

        assertEquals(0, assertNotNull(message.set_fixed_position).altitude)
    }

    @Test
    fun `zero coordinates remove the fixed position whether or not altitude is present`() {
        assertEquals(true, Position(0.0, 0.0, null).toFixedPositionAdminMessage().remove_fixed_position)
        assertTrue(Position(0.0, 0.0, 0).isFixedPositionRemoval())
    }
}

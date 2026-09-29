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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.meshtastic.proto.Position as ProtoPosition

class PositionFixTest {

    private fun position(latitude: Int?, longitude: Int?) = ProtoPosition.Builder()
        .also { wb ->
            wb.latitude_i = latitude
            wb.longitude_i = longitude
            wb.time = 1_700_000_000
        }
        .build()

    @Test
    fun `a position without latitude has no fix`() {
        val position = position(latitude = null, longitude = 134_050_000)

        assertNull(position.fixOrNull())
        assertFalse(position.hasFix())
    }

    @Test
    fun `a position without longitude has no fix`() {
        val position = position(latitude = 525_200_000, longitude = null)

        assertNull(position.fixOrNull())
        assertFalse(position.hasFix())
    }

    @Test
    fun `a position at zero latitude and zero longitude has no fix`() {
        val position = position(latitude = 0, longitude = 0)

        assertNull(position.fixOrNull())
        assertFalse(position.hasFix())
    }

    @Test
    fun `a position on the equator has a fix`() {
        val position = position(latitude = 0, longitude = 134_050_000)

        assertEquals(0 to 134_050_000, position.fixOrNull())
        assertTrue(position.hasFix())
    }

    @Test
    fun `a position on the prime meridian has a fix`() {
        val position = position(latitude = 525_200_000, longitude = 0)

        assertEquals(525_200_000 to 0, position.fixOrNull())
        assertTrue(position.hasFix())
    }

    @Test
    fun `a position with both axes has a fix`() {
        val position = position(latitude = 525_200_000, longitude = 134_050_000)

        assertEquals(525_200_000 to 134_050_000, position.fixOrNull())
        assertTrue(position.hasFix())
    }
}

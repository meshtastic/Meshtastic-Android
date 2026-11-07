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
package org.meshtastic.feature.map.terrain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MapterhornEndpointsTest {

    @Test
    fun `a regional archive is named by its zoom and z6 tile`() {
        assertEquals(
            "https://download.mapterhorn.com/6-10-22.pmtiles",
            MapterhornEndpoints.regionalArchiveUrl(TileIndex(zoom = 6, x = 10, y = 22)),
        )
    }

    @Test
    fun `a regional archive rejects a tile at any other zoom`() {
        assertFailsWith<IllegalArgumentException> {
            MapterhornEndpoints.regionalArchiveUrl(TileIndex(zoom = 7, x = 20, y = 44))
        }
    }
}

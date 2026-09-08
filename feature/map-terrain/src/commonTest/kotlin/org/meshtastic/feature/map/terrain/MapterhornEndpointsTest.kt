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
import kotlin.test.assertNull

class MapterhornEndpointsTest {

    @Test
    fun `a tiny region resolves a regional archive URL`() {
        val seattle = GeoBounds(south = 47.6, west = -122.4, north = 47.61, east = -122.39)
        val url = MapterhornEndpoints.regionalUrlFor(seattle)
        assertEquals(true, url?.startsWith("https://download.mapterhorn.com/"))
        assertEquals(true, url?.endsWith(".pmtiles"))
    }

    @Test
    fun `a region spanning multiple z6 tiles has no regional archive`() {
        val global = GeoBounds(south = -60.0, west = -170.0, north = 60.0, east = 170.0)
        assertNull(MapterhornEndpoints.regionalUrlFor(global))
    }
}

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
package org.meshtastic.app.map

import com.google.maps.android.compose.MapType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleMapTileLayerSelectionTest {

    @Test
    fun `local Burning Man pack takes precedence over a custom tile source inside coverage`() {
        val selection =
            googleMapTileLayerSelection(
                selectedMapType = MapType.HYBRID,
                hasCustomTileSource = true,
                burningManPackCoversCamera = true,
            )

        assertEquals(MapType.NONE, selection.mapType)
        assertFalse(selection.attachCustomTileSource)
    }

    @Test
    fun `custom tile source remains available outside Burning Man coverage`() {
        val selection =
            googleMapTileLayerSelection(
                selectedMapType = MapType.HYBRID,
                hasCustomTileSource = true,
                burningManPackCoversCamera = false,
            )

        assertEquals(MapType.NONE, selection.mapType)
        assertTrue(selection.attachCustomTileSource)
    }
}

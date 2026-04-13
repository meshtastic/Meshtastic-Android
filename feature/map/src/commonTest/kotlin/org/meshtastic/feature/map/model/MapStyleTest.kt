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
package org.meshtastic.feature.map.model

import org.maplibre.compose.style.BaseStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MapStyleTest {

    @Test
    fun toBaseStyle_returnsUriWithCorrectStyleUri() {
        for (style in MapStyle.entries) {
            val baseStyle = style.toBaseStyle()
            assertIs<BaseStyle.Uri>(baseStyle)
            assertEquals(style.styleUri, baseStyle.uri)
        }
    }

    @Test
    fun allStyles_haveNonBlankUri() {
        for (style in MapStyle.entries) {
            assert(style.styleUri.isNotBlank()) { "${style.name} has a blank styleUri" }
        }
    }

    @Test
    fun openStreetMap_isDefault() {
        // Verify OpenStreetMap is the first entry (used as default throughout the app)
        assertEquals(MapStyle.OpenStreetMap, MapStyle.entries.first())
    }
}

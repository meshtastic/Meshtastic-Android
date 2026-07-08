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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapLayerResolutionTest {
    @Test
    fun resolvesGeoJsonExtensionsAndMimes() {
        assertEquals(LayerType.GEOJSON, resolveLayerType("geojson"))
        assertEquals(LayerType.GEOJSON, resolveLayerType("json"))
        assertEquals(LayerType.GEOJSON, resolveLayerType("GeoJSON")) // case-insensitive
        assertEquals(LayerType.GEOJSON, resolveLayerType("geo+json")) // content-resolver MIME subtype
        assertEquals(LayerType.GEOJSON, resolveLayerType("vnd.geo+json"))
    }

    @Test
    fun resolvesKmlExtensionsAndMimes() {
        assertEquals(LayerType.KML, resolveLayerType("kml"))
        assertEquals(LayerType.KML, resolveLayerType("kmz"))
        assertEquals(LayerType.KML, resolveLayerType("vnd.google-earth.kml+xml"))
        assertEquals(LayerType.KML, resolveLayerType("vnd.google-earth.kmz"))
    }

    @Test
    fun rejectsUnsupportedAndNull() {
        assertNull(resolveLayerType("txt"))
        assertNull(resolveLayerType(""))
        assertNull(resolveLayerType(null))
    }
}

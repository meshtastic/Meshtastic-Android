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
package org.meshtastic.feature.map.mapcompose

import org.meshtastic.feature.map.mapcompose.tile.TileSourceCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class TileSourceTest {

    // For slippy tile (zoom=10, col/x=163, row/y=395):
    private val zoom = 10
    private val row = 395
    private val col = 163

    @Test
    fun osmScheme_ordersColumnBeforeRow() {
        assertEquals("https://tile.openstreetmap.org/10/163/395.png", TileSourceCatalog.OSM_MAPNIK.tileUrl(zoom, row, col))
    }

    @Test
    fun esriScheme_ordersRowBeforeColumn() {
        assertEquals(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/10/395/163.jpg",
            TileSourceCatalog.ESRI_WORLD_TOPO.tileUrl(zoom, row, col),
        )
    }

    @Test
    fun usgsScheme_ordersRowBeforeColumn_withoutExtension() {
        assertEquals(
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/10/395/163",
            TileSourceCatalog.USGS_TOPO.tileUrl(zoom, row, col),
        )
    }

    @Test
    fun catalogIds_areUnique() {
        val ids = TileSourceCatalog.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun catalogEntries_haveAttributionAndSaneZooms() {
        TileSourceCatalog.ALL.forEach { source ->
            assertTrue(source.attribution.isNotBlank(), "${source.id} missing attribution")
            assertTrue(source.minZoom < source.maxZoom, "${source.id} zoom range invalid")
            assertTrue(source.baseUrl.startsWith("https://"), "${source.id} not https")
            assertTrue(source.baseUrl.endsWith("/"), "${source.id} baseUrl must end with '/'")
        }
    }

    @Test
    fun byId_resolvesEveryCatalogEntry_andFallsBackToDefault() {
        TileSourceCatalog.ALL.forEach { source -> assertEquals(source, TileSourceCatalog.byId(source.id)) }
        assertEquals(TileSourceCatalog.DEFAULT, TileSourceCatalog.byId(null))
        assertEquals(TileSourceCatalog.DEFAULT, TileSourceCatalog.byId("no_such_source"))
        assertEquals(TileSourceCatalog.OSM_MAPNIK, TileSourceCatalog.DEFAULT)
    }
}

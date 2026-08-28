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
package org.meshtastic.feature.map.tiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapTileCatalogueTest {
    @Test
    fun `every source id is unique across basemaps and overlays`() {
        val ids = MapTileCatalogue.basemaps.map { it.id } + MapTileCatalogue.overlays.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every raster basemap declares attribution`() {
        MapTileCatalogue.basemaps.forEach { basemap ->
            assertNotNull(basemap.spec.attributionHtml, "${basemap.id} must attribute its tiles")
        }
    }

    @Test
    fun `hillshade is terrarium encoded`() {
        // MapLibre defaults raster-DEM to Mapbox Terrain-RGB, and the mismatch fails silently: shading still renders,
        // it is just wrong. Pin the encoding here.
        assertEquals(DemEncoding.TERRARIUM, MapTileCatalogue.Hillshade.demEncoding)
    }

    @Test
    fun `only the elevation source is DEM encoded`() {
        // A renderer without hillshading has to drop DEM sources rather than draw them; that filter is only correct if
        // the imagery overlays leave the encoding unset.
        val demSources = MapTileCatalogue.overlays.filter { it.demEncoding != null }

        assertEquals(listOf(MapTileCatalogue.Hillshade.id), demSources.map { it.id })
    }

    @Test
    fun `the NOAA overlay requests projected bounds`() {
        val url = MapTileCatalogue.NoaaRadar.spec.tiles.single()

        assertTrue(url.contains("{bbox-epsg-3857}"), "WMS needs the bbox placeholder, not z/x/y")
    }

    @Test
    fun `the NOAA overlay does not point at the retired nowCOAST host`() {
        // This assertion exists because the placeholder check above passed for a year against a hostname that had
        // stopped resolving, so the layer drew nothing and nothing failed. A unit test cannot reach the network, but it
        // can refuse the one host that is known to be gone.
        val url = MapTileCatalogue.NoaaRadar.spec.tiles.single()

        assertFalse(url.contains("nowcoast.noaa.gov"), "new.nowcoast.noaa.gov is NXDOMAIN; the layer draws nothing")
    }

    @Test
    fun `the NOAA overlay asks for the layer its url names`() {
        // A WMS GetMap silently returns a blank tile when LAYERS names something the service does not publish, so the
        // layer in the path and the layer in the query have to agree.
        val url = MapTileCatalogue.NoaaRadar.spec.tiles.single()
        val layer = url.substringAfter("LAYERS=").substringBefore("&")

        assertTrue(url.contains("/$layer/ows"), "LAYERS=$layer is not the layer the path requests")
    }

    @Test
    fun `OpenWeather is offered only when a key is supplied`() {
        assertNull(MapTileCatalogue.openWeatherPrecipitation(""))
        assertNull(MapTileCatalogue.openWeatherPrecipitation("   "))
        assertNotNull(MapTileCatalogue.openWeatherPrecipitation("abc123"))
    }

    @Test
    fun `every catalogue source resolves a tile url at its own minimum zoom`() {
        // A template whose placeholders we cannot fill yields no URL at all, so this catches a typo'd source at build
        // time rather than as an empty layer nobody reports.
        (MapTileCatalogue.basemaps.map { it.id to it.spec } + MapTileCatalogue.overlays.map { it.id to it.spec })
            .forEach { (id, spec) ->
                assertNotNull(spec.tileUrl(x = 0, y = 0, zoom = spec.minZoom), "$id resolved no tile url")
            }
    }
}

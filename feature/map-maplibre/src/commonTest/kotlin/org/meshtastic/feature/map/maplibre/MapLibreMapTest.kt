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
package org.meshtastic.feature.map.maplibre

import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.feature.map.maplibre.geojson.circlePolygon
import org.meshtastic.feature.map.maplibre.geojson.destination
import org.meshtastic.feature.map.maplibre.geojson.nodesToFeatureCollection
import org.meshtastic.feature.map.maplibre.geojson.precisionMeters
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.Basemaps
import org.meshtastic.feature.map.maplibre.style.MapOverlays
import org.meshtastic.proto.Position
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun node(
    num: Int,
    latitude: Double,
    longitude: Double,
    lastHeard: Int = 0,
    isFavorite: Boolean = false,
    precisionBits: Int = 0,
) = Node(
    num = num,
    position =
    Position(
        latitude_i = (latitude * 1e7).toInt(),
        longitude_i = (longitude * 1e7).toInt(),
        precision_bits = precisionBits,
    ),
    lastHeard = lastHeard,
    isFavorite = isFavorite,
)

class BasemapRegistryTest {
    @Test
    fun `every basemap id is unique`() {
        val ids = Basemaps.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `unknown id falls back to the default rather than throwing`() {
        assertEquals(Basemaps.default, Basemaps.byId("no-such-basemap"))
        assertEquals(Basemaps.default, Basemaps.byId(null))
    }

    @Test
    fun `the default basemap is the maintained OpenFreeMap style`() {
        val default = Basemaps.default
        assertTrue(default is Basemap.Vector)
        assertEquals("https://tiles.openfreemap.org/styles/liberty", default.styleUri)
    }

    @Test
    fun `every raster basemap declares attribution`() {
        Basemaps.all.filterIsInstance<Basemap.Raster>().forEach { basemap ->
            assertNotNull(basemap.spec.attributionHtml, "${basemap.id} must attribute its tiles")
        }
    }

    @Test
    fun `hillshade is terrarium encoded`() {
        // MapLibre defaults raster-DEM to Mapbox Terrain-RGB, and the mismatch fails silently:
        // shading still renders, it is just wrong. Pin the encoding here.
        assertEquals(
            MapOverlays.Hillshade.encoding,
            org.meshtastic.feature.map.maplibre.style.MapOverlay.DemEncoding.TERRARIUM,
        )
    }

    @Test
    fun `the NOAA overlay requests projected bounds`() {
        val url = MapOverlays.NoaaRadar.spec.tiles.single()
        assertTrue(url.contains("{bbox-epsg-3857}"), "WMS needs the bbox placeholder, not z/x/y")
    }

    @Test
    fun `OpenWeather is offered only when a key is supplied`() {
        assertNull(MapOverlays.openWeatherPrecipitation(""))
        assertNull(MapOverlays.openWeatherPrecipitation("   "))
        assertNotNull(MapOverlays.openWeatherPrecipitation("abc123"))
    }
}

class PrecisionCircleTest {
    @Test
    fun `precision table matches the values the OSMdroid marker used`() {
        assertEquals(23345.484932, precisionMeters(10))
        assertEquals(45.58554, precisionMeters(19))
    }

    @Test
    fun `an undegraded position has no uncertainty circle`() {
        assertNull(precisionMeters(0))
        assertNull(precisionMeters(32))
    }

    @Test
    fun `walking a known distance north lands the expected distance away`() {
        val start = 45.0
        val moved = destination(start, 0.0, 111_195.0, 0.0)
        // ~1 degree of latitude, within 100 m.
        assertTrue(abs(moved.latitude - (start + 1.0)) < 0.001, "got ${moved.latitude}")
    }

    @Test
    fun `a circle ring is closed`() {
        val ring = circlePolygon(45.0, -122.0, 1000.0).coordinates.single()
        assertEquals(ring.first(), ring.last())
    }
}

class MapGeometryTest {
    private fun filters(onlyFavorites: Boolean = false, lastHeard: LastHeardFilter = LastHeardFilter.Any) =
        BaseMapViewModel.MapFilterState(
            onlyFavorites = onlyFavorites,
            showWaypoints = true,
            showPrecisionCircle = true,
            lastHeardFilter = lastHeard,
            lastHeardTrackFilter = LastHeardFilter.Any,
        )

    @Test
    fun `nodes without a fix never reach the map`() {
        val nodes = listOf(node(1, 0.0, 0.0), node(2, 45.0, -122.0))
        assertEquals(listOf(2), filterNodesForMap(nodes, filters(), nowSeconds = 0).map { it.num })
    }

    @Test
    fun `favourites filter keeps only favourites`() {
        val nodes = listOf(node(1, 45.0, -122.0), node(2, 45.1, -122.1, isFavorite = true))
        assertEquals(listOf(2), filterNodesForMap(nodes, filters(onlyFavorites = true), nowSeconds = 0).map { it.num })
    }

    @Test
    fun `last heard filter drops nodes outside the window`() {
        val nodes = listOf(node(1, 45.0, -122.0, lastHeard = 0), node(2, 45.1, -122.1, lastHeard = 9_000))
        val kept = filterNodesForMap(nodes, filters(lastHeard = LastHeardFilter.OneHour), nowSeconds = 10_000)
        assertEquals(listOf(2), kept.map { it.num })
    }

    @Test
    fun `no located nodes yields no bounding box`() {
        assertNull(nodesBoundingBox(emptyList()))
        assertNull(nodesBoundingBox(listOf(node(1, 0.0, 0.0))))
    }

    @Test
    fun `a single node is padded into a box the camera can frame`() {
        val box = assertNotNull(nodesBoundingBox(listOf(node(1, 45.0, -122.0))))
        assertTrue(box.northeast.latitude > box.southwest.latitude)
        assertTrue(box.northeast.longitude > box.southwest.longitude)
    }

    @Test
    fun `the bounding box spans every located node`() {
        val box = assertNotNull(nodesBoundingBox(listOf(node(1, 40.0, -125.0), node(2, 48.0, -118.0))))
        assertEquals(40.0, box.southwest.latitude, 1e-6)
        assertEquals(48.0, box.northeast.latitude, 1e-6)
        assertEquals(-125.0, box.southwest.longitude, 1e-6)
        assertEquals(-118.0, box.northeast.longitude, 1e-6)
    }
}

class NodeFeatureTest {
    @Test
    fun `unlocated nodes are dropped instead of landing at null island`() {
        val collection = nodesToFeatureCollection(listOf(node(1, 0.0, 0.0), node(2, 45.0, -122.0)))
        assertEquals(1, collection.features.size)
    }

    @Test
    fun `the connected node is flagged so it can be styled apart`() {
        val collection = nodesToFeatureCollection(listOf(node(7, 45.0, -122.0)), myNodeNum = 7)
        val properties = assertNotNull(collection.features.single().properties)
        assertEquals("true", properties["isSelf"].toString())
    }
}

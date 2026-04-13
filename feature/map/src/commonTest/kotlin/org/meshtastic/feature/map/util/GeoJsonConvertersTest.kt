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
package org.meshtastic.feature.map.util

import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import org.meshtastic.proto.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class GeoJsonConvertersTest {

    // --- nodesToFeatureCollection ---

    @Test
    fun nodesToFeatureCollection_emptyList_returnsEmptyCollection() {
        val result = nodesToFeatureCollection(emptyList())
        assertTrue(result.features.isEmpty())
    }

    @Test
    fun nodesToFeatureCollection_skipsNodesWithoutPosition() {
        val node = Node(num = 1, position = Position())
        val result = nodesToFeatureCollection(listOf(node))
        assertTrue(result.features.isEmpty())
    }

    @Test
    fun nodesToFeatureCollection_skipsZeroLatLng() {
        val node = Node(num = 1, position = Position(latitude_i = 0, longitude_i = 0))
        val result = nodesToFeatureCollection(listOf(node))
        assertTrue(result.features.isEmpty())
    }

    @Test
    fun nodesToFeatureCollection_convertsValidNode() {
        val node =
            Node(
                num = 42,
                user = User(short_name = "AB", long_name = "Alpha Bravo"),
                position = Position(latitude_i = 400000000, longitude_i = -740000000),
                lastHeard = 1000,
                isFavorite = true,
                hopsAway = 2,
                viaMqtt = false,
                snr = 5.5f,
                rssi = -80,
            )
        val result = nodesToFeatureCollection(listOf(node), myNodeNum = 42)
        assertEquals(1, result.features.size)

        val feature = result.features.first()
        val coords = feature.geometry.coordinates
        assertEquals(40.0, coords.latitude, 0.001)
        assertEquals(-74.0, coords.longitude, 0.001)

        val props = feature.properties
        assertEquals(42, props["node_num"]?.toString()?.toIntOrNull())
        assertEquals("\"AB\"", props["short_name"].toString())
        assertEquals("\"Alpha Bravo\"", props["long_name"].toString())
        assertEquals("true", props["is_favorite"].toString())
        assertEquals("true", props["is_my_node"].toString())
    }

    @Test
    fun nodesToFeatureCollection_isMyNodeFalseForOtherNodes() {
        val node = Node(num = 10, position = Position(latitude_i = 400000000, longitude_i = -740000000))
        val result = nodesToFeatureCollection(listOf(node), myNodeNum = 42)
        val props = result.features.first().properties
        assertEquals("false", props["is_my_node"].toString())
    }

    @Test
    fun nodesToFeatureCollection_multipleNodes() {
        val nodes =
            listOf(
                Node(num = 1, position = Position(latitude_i = 100000000, longitude_i = 200000000)),
                Node(num = 2, position = Position(latitude_i = 300000000, longitude_i = 400000000)),
            )
        val result = nodesToFeatureCollection(nodes)
        assertEquals(2, result.features.size)
    }

    // --- waypointsToFeatureCollection ---

    @Test
    fun waypointsToFeatureCollection_emptyMap_returnsEmptyCollection() {
        val result = waypointsToFeatureCollection(emptyMap())
        assertTrue(result.features.isEmpty())
    }

    @Test
    fun waypointsToFeatureCollection_skipsZeroLatLng() {
        val waypoint = Waypoint(id = 1, latitude_i = 0, longitude_i = 0, name = "Test")
        val packet = DataPacket("dest", 0, waypoint)
        val result = waypointsToFeatureCollection(mapOf(1 to packet))
        assertTrue(result.features.isEmpty())
    }

    @Test
    fun waypointsToFeatureCollection_convertsValidWaypoint() {
        val waypoint =
            Waypoint(
                id = 99,
                name = "Home",
                description = "My house",
                icon = 0x1F3E0, // House emoji
                locked_to = 42,
                latitude_i = 515000000,
                longitude_i = -1000000,
                expire = 0,
            )
        val packet = DataPacket("dest", 0, waypoint)
        val result = waypointsToFeatureCollection(mapOf(99 to packet))

        assertEquals(1, result.features.size)
        val feature = result.features.first()
        val coords = feature.geometry.coordinates
        assertEquals(51.5, coords.latitude, 0.001)
        assertEquals(-0.1, coords.longitude, 0.001)

        val props = feature.properties
        assertEquals(99, props["waypoint_id"]?.toString()?.toIntOrNull())
        assertEquals("\"Home\"", props["name"].toString())
    }

    // --- positionsToLineString ---

    @Test
    fun positionsToLineString_lessThanTwoPositions_returnsEmptyCollection() {
        val result = positionsToLineString(listOf(Position(latitude_i = 100000000, longitude_i = 200000000)))
        assertTrue(result.features.isEmpty())
    }

    @Test
    fun positionsToLineString_emptyList_returnsEmptyCollection() {
        val result = positionsToLineString(emptyList())
        assertTrue(result.features.isEmpty())
    }

    @Test
    fun positionsToLineString_validPositions_createsLineString() {
        val positions =
            listOf(
                Position(latitude_i = 100000000, longitude_i = 200000000),
                Position(latitude_i = 110000000, longitude_i = 210000000),
                Position(latitude_i = 120000000, longitude_i = 220000000),
            )
        val result = positionsToLineString(positions)
        assertEquals(1, result.features.size)
    }

    @Test
    fun positionsToLineString_skipsZeroCoords() {
        val positions =
            listOf(
                Position(latitude_i = 100000000, longitude_i = 200000000),
                Position(latitude_i = 0, longitude_i = 0),
                Position(latitude_i = 120000000, longitude_i = 220000000),
            )
        val result = positionsToLineString(positions)
        assertEquals(1, result.features.size)
    }

    // --- positionsToPointFeatures ---

    @Test
    fun positionsToPointFeatures_emptyList_returnsEmptyCollection() {
        val result = positionsToPointFeatures(emptyList())
        assertTrue(result.features.isEmpty())
    }

    @Test
    fun positionsToPointFeatures_convertsValidPositions() {
        val positions = listOf(Position(latitude_i = 400000000, longitude_i = -740000000, time = 1000, altitude = 100))
        val result = positionsToPointFeatures(positions)
        assertEquals(1, result.features.size)
        val props = result.features.first().properties
        assertEquals("\"1000\"", props["time"].toString())
        assertEquals(100, props["altitude"]?.toString()?.toIntOrNull())
    }

    // --- precisionBitsToMeters ---

    @Test
    fun precisionBitsToMeters_knownValues() {
        assertEquals(5886.0, precisionBitsToMeters(10))
        assertEquals(2944.0, precisionBitsToMeters(11))
        assertEquals(1472.0, precisionBitsToMeters(12))
        assertEquals(736.0, precisionBitsToMeters(13))
        assertEquals(368.0, precisionBitsToMeters(14))
        assertEquals(184.0, precisionBitsToMeters(15))
        assertEquals(92.0, precisionBitsToMeters(16))
        assertEquals(46.0, precisionBitsToMeters(17))
        assertEquals(23.0, precisionBitsToMeters(18))
        assertEquals(11.5, precisionBitsToMeters(19))
    }

    @Test
    fun precisionBitsToMeters_outOfRange_returnsZero() {
        assertEquals(0.0, precisionBitsToMeters(0))
        assertEquals(0.0, precisionBitsToMeters(9))
        assertEquals(0.0, precisionBitsToMeters(20))
        assertEquals(0.0, precisionBitsToMeters(-1))
    }

    // --- intToHexColor ---

    @Test
    fun intToHexColor_basicColors() {
        assertEquals("#FF0000", intToHexColor(0xFFFF0000.toInt())) // Red
        assertEquals("#00FF00", intToHexColor(0xFF00FF00.toInt())) // Green
        assertEquals("#0000FF", intToHexColor(0xFF0000FF.toInt())) // Blue
        assertEquals("#000000", intToHexColor(0xFF000000.toInt())) // Black
        assertEquals("#FFFFFF", intToHexColor(0xFFFFFFFF.toInt())) // White
    }

    @Test
    fun intToHexColor_stripsAlpha() {
        // Alpha channel should be stripped — only RGB remains
        assertEquals("#6750A4", intToHexColor(0xFF6750A4.toInt()))
        assertEquals("#6750A4", intToHexColor(0x006750A4))
    }

    @Test
    fun intToHexColor_padsSixDigits() {
        assertEquals("#000001", intToHexColor(1))
        assertEquals("#000100", intToHexColor(0x100))
    }

    // --- convertIntToEmoji ---

    @Test
    fun convertIntToEmoji_bmpCharacter() {
        // 0x2764 = Heart character (❤)
        assertEquals("\u2764", convertIntToEmoji(0x2764))
    }

    @Test
    fun convertIntToEmoji_supplementaryCharacter() {
        // 0x1F4CD = Round Pushpin (📍)
        assertEquals("\uD83D\uDCCD", convertIntToEmoji(0x1F4CD))
    }

    @Test
    fun convertIntToEmoji_houseEmoji() {
        // 0x1F3E0 = House (🏠)
        val result = convertIntToEmoji(0x1F3E0)
        assertEquals(2, result.length) // Surrogate pair
    }

    @Test
    fun convertIntToEmoji_maxBmpCharacter() {
        val result = convertIntToEmoji(0xFFFF)
        assertEquals(1, result.length)
    }

    @Test
    fun convertIntToEmoji_negativeCodepoint_returnsNonEmptyString() {
        // Negative code points wrap around in char conversion but should not crash
        val result = convertIntToEmoji(-1)
        assertTrue(result.isNotEmpty(), "Should return a non-empty string even for invalid code points")
    }

    // --- toGeoPositionOrNull ---

    @Test
    fun toGeoPositionOrNull_validCoords_returnsGeoPosition() {
        val result = toGeoPositionOrNull(400000000, -740000000)
        assertNotNull(result)
        assertEquals(40.0, result.latitude, 0.001)
        assertEquals(-74.0, result.longitude, 0.001)
    }

    @Test
    fun toGeoPositionOrNull_zeroCoords_returnsNull() {
        val result = toGeoPositionOrNull(0, 0)
        assertNull(result)
    }

    @Test
    fun toGeoPositionOrNull_nullCoords_returnsNull() {
        val result = toGeoPositionOrNull(null, null)
        assertNull(result)
    }

    @Test
    fun toGeoPositionOrNull_onlyLatNull_treatedAsZero() {
        // null lat = 0, non-zero lng -> lat=0.0 && lng!=0.0 -> not both zero -> returns position
        val result = toGeoPositionOrNull(null, 100000000)
        assertNotNull(result)
        assertEquals(0.0, result.latitude, 0.001)
        assertEquals(10.0, result.longitude, 0.001)
    }

    // --- typedFeatureCollection ---

    @Test
    fun typedFeatureCollection_preservesFeatures() {
        val features =
            listOf(
                Feature(
                    geometry = Point(org.maplibre.spatialk.geojson.Position(longitude = 1.0, latitude = 2.0)),
                    properties = null,
                ),
            )
        val result = typedFeatureCollection(features)
        assertEquals(1, result.features.size)
    }

    // --- computeBoundingBox ---

    @Test
    fun computeBoundingBox_fewerThanTwoPositions_returnsNull() {
        assertNull(computeBoundingBox(emptyList()))
        assertNull(computeBoundingBox(listOf(org.maplibre.spatialk.geojson.Position(longitude = 1.0, latitude = 2.0))))
    }

    @Test
    fun computeBoundingBox_twoOrMorePositions_returnsBounds() {
        val positions =
            listOf(
                org.maplibre.spatialk.geojson.Position(longitude = -74.0, latitude = 40.0),
                org.maplibre.spatialk.geojson.Position(longitude = -73.0, latitude = 41.0),
                org.maplibre.spatialk.geojson.Position(longitude = -75.0, latitude = 39.0),
            )
        val bbox = computeBoundingBox(positions)
        assertNotNull(bbox)
        assertEquals(39.0, bbox.southwest.latitude, 0.001)
        assertEquals(-75.0, bbox.southwest.longitude, 0.001)
        assertEquals(41.0, bbox.northeast.latitude, 0.001)
        assertEquals(-73.0, bbox.northeast.longitude, 0.001)
    }
}

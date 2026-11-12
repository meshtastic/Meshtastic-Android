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
package org.meshtastic.feature.map.layers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapLayerSourceTest {

    private fun String.rootType() = encodeToByteArray().jsonRootType()

    @Test
    fun aFeatureCollectionIsGeoJson() {
        assertTrue("""{"type":"FeatureCollection","features":[]}""".encodeToByteArray().looksLikeGeoJson())
    }

    @Test
    fun aBareGeometryIsGeoJson() {
        val polygon = """{"type": "Polygon", "coordinates": [[[0,0],[1,0],[1,1],[0,0]]]}"""
        assertTrue(polygon.encodeToByteArray().looksLikeGeoJson())
    }

    @Test
    fun theRootTypeIsFoundAfterALargerMember() {
        val json =
            """{"features":[{"type":"Feature","properties":{"type":"x"},"geometry":null}],"type":"FeatureCollection"}"""
        assertEquals("FeatureCollection", json.rootType())
    }

    @Test
    fun aNestedTypeIsNotTheRootType() {
        assertNull("""{"compilerOptions":{"type":"Feature"}}""".rootType())
    }

    @Test
    fun aPackageManifestIsNotGeoJson() {
        assertFalse("""{"name":"app","version":"1.0.0","type":"module"}""".encodeToByteArray().looksLikeGeoJson())
    }

    @Test
    fun aNonStringTypeIsNotGeoJson() {
        assertNull("""{"type":7}""".rootType())
    }

    @Test
    fun anArrayRootIsNotGeoJson() {
        assertNull("""[{"type":"Feature"}]""".rootType())
    }

    @Test
    fun aTypeInsideAStringValueIsIgnored() {
        assertNull("""{"note":"\"type\":\"Feature\" , {","kind":"x"}""".rootType())
    }

    @Test
    fun aByteOrderMarkAndLeadingWhitespaceAreSkipped() {
        val bytes =
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "\n  {\"type\":\"Feature\"}".encodeToByteArray()
        assertEquals("Feature", bytes.jsonRootType())
    }

    @Test
    fun truncatedAndEmptyInputAreNotGeoJson() {
        assertNull("""{"type":"Featu""".rootType())
        assertNull("".rootType())
        assertNull("not json".rootType())
    }

    @Test
    fun aComplexKeyBeforeTypeDoesNotHideIt() {
        assertEquals("Feature", """{"bbox":[0,0,1,1],"properties":{"a":{"b":[1,2]}},"type":"Feature"}""".rootType())
    }

    @Test
    fun geoJsonAndCoverageShareTheParserCeilingAndKmlTheArchiveCeiling() {
        assertEquals(MAX_GEOJSON_LAYER_BYTES, LayerType.GEOJSON.maxSourceBytes())
        assertEquals(MAX_GEOJSON_LAYER_BYTES, LayerType.COVERAGE.maxSourceBytes())
        assertEquals(MAX_KMZ_INFLATED_BYTES, LayerType.KML.maxSourceBytes())
    }
}

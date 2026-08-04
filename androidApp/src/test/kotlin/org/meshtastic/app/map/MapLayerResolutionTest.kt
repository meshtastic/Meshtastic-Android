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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun resolvesCoverageExtension() {
        assertEquals(LayerType.COVERAGE, resolveLayerType(COVERAGE_EXTENSION))
        assertEquals(LayerType.COVERAGE, resolveLayerType("COVERAGE")) // case-insensitive
    }

    @Test
    fun rejectsUnsupportedAndNull() {
        assertNull(resolveLayerType("txt"))
        assertNull(resolveLayerType(""))
        assertNull(resolveLayerType(null))
    }

    @Test
    fun stripsTrailingUuidFromDisplayName() {
        // Coverage estimates are written as "<name>_<uuid>.coverage"; the UUID must not reach the layers sheet.
        assertEquals("Coverage", displayNameFromFileName("Coverage_3f2a1b9c-4d5e-6f70-8192-a3b4c5d6e7f8"))
        assertEquals("Base_Camp", displayNameFromFileName("Base_Camp_3F2A1B9C-4D5E-6F70-8192-A3B4C5D6E7F8"))
    }

    @Test
    fun buildsDistinctFileNamesForLayersSharingADisplayName() {
        // Regression: file imports once used the bare display name, so importing two different route.kml files
        // truncated the first layer's data while both still showed as separate rows.
        val first = layerFileName("route", "kml")
        val second = layerFileName("route", "kml")
        assertNotEquals(first, second)
        assertTrue(first.startsWith("route_"), "expected display name prefix, got $first")
        assertTrue(first.endsWith(".kml"), "expected extension preserved, got $first")
    }

    @Test
    fun fileNameRoundTripsBackToTheDisplayName() {
        assertEquals("route", displayNameFromFileName(layerFileName("route", "kml").substringBeforeLast('.')))
        assertEquals(
            "Coverage",
            displayNameFromFileName(layerFileName("Coverage", COVERAGE_EXTENSION).substringBeforeLast('.')),
        )
    }

    @Test
    fun sanitizesPathSeparatorsOutOfFileNames() {
        val name = layerFileName("../../etc/passwd", "kml")
        assertFalse(name.contains('/'), "path separators must not survive: $name")
        assertTrue(name.startsWith(".._.._etc_passwd_"), "unexpected sanitized form: $name")
    }

    @Test
    fun leavesNamesWithoutUuidSuffixAlone() {
        assertEquals("my_route", displayNameFromFileName("my_route"))
        assertEquals("Coverage", displayNameFromFileName("Coverage"))
        // A trailing underscore-number must not be mistaken for a UUID.
        assertEquals("Coverage_2", displayNameFromFileName("Coverage_2"))
    }
}

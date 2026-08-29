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
package org.meshtastic.feature.map.geojson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun feature(properties: String) =
    """{"type":"Feature","properties":$properties,"geometry":{"type":"Point","coordinates":[0,0]}}"""

private fun collection(vararg features: String) =
    """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

class IconUrlsTest {
    @Test
    fun `every distinct icon in the document is found once`() {
        val geoJson =
            collection(
                feature("""{"icon-url":"https://example.org/a.png"}"""),
                feature("""{"icon-url":"https://example.org/b.png"}"""),
                feature("""{"icon-url":"https://example.org/a.png"}"""),
            )

        assertEquals(setOf("https://example.org/a.png", "https://example.org/b.png"), geoJsonIconUrls(geoJson))
    }

    @Test
    fun `a document with no icons asks for none`() {
        assertEquals(emptySet(), geoJsonIconUrls(collection(feature("""{"title":"no icon here"}"""))))
    }

    @Test
    fun `features without properties are skipped rather than throwing`() {
        val geoJson = """{"type":"FeatureCollection","features":[{"type":"Feature"},null]}"""

        assertEquals(emptySet(), geoJsonIconUrls(geoJson))
    }

    @Test
    fun `a blank icon is not an icon`() {
        assertEquals(emptySet(), geoJsonIconUrls(collection(feature("""{"icon-url":"   "}"""))))
    }

    @Test
    fun `an icon that is not a string does not take the whole document down`() {
        val geoJson =
            collection(
                feature("""{"icon-url":{"nested":"object"}}"""),
                feature("""{"icon-url":"https://example.org/good.png"}"""),
            )

        assertEquals(setOf("https://example.org/good.png"), geoJsonIconUrls(geoJson))
    }

    @Test
    fun `a document that will not parse yields no icons rather than failing the layer`() {
        assertEquals(emptySet(), geoJsonIconUrls("not json at all"))
        assertEquals(emptySet(), geoJsonIconUrls(""))
    }

    @Test
    fun `the icon count is capped so one import cannot fill the style with images`() {
        val many = (1..200).map { feature("""{"icon-url":"https://example.org/$it.png"}""") }

        assertTrue(geoJsonIconUrls(collection(*many.toTypedArray())).size <= 64)
    }
}

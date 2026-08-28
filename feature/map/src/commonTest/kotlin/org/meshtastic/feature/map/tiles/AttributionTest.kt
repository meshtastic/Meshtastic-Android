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
import kotlin.test.assertTrue

class AttributionTest {
    @Test
    fun `a link becomes its text`() {
        assertEquals(
            "© OpenStreetMap",
            "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>".attributionPlainText(),
        )
    }

    @Test
    fun `an escaped ampersand does not turn into a live entity`() {
        // Decoding &amp; first would leave a bare "&copy;" for the next pass to render as ©.
        assertEquals("&copy; Example", "&amp;copy; Example".attributionPlainText())
    }

    @Test
    fun `every catalogue attribution renders as displayable text`() {
        // The credit is not optional — OpenStreetMap's and Esri's tile policies both require it — so a source whose
        // attribution collapsed to nothing after tag stripping would be a silent compliance failure.
        (MapTileCatalogue.basemaps.map { it.spec } + MapTileCatalogue.overlays.map { it.spec }).forEach { spec ->
            val text = spec.attributionHtml?.attributionPlainText()
            assertTrue(!text.isNullOrBlank(), "an attribution rendered as nothing")
            assertFalse('<' in text || '&' in text, "markup survived into displayed text: $text")
        }
    }

    @Test
    fun `basemap and overlay credits are joined without repeating one`() {
        val shared = RasterTileSpec(tiles = listOf("https://a/{z}/{x}/{y}.png"), attributionHtml = "NOAA")

        assertEquals("NOAA", mapAttributionText(basemap = shared, overlays = listOf(shared)))
    }

    @Test
    fun `nothing to credit is an empty line rather than a stray separator`() {
        assertEquals("", mapAttributionText(basemap = null, overlays = emptyList()))
    }
}

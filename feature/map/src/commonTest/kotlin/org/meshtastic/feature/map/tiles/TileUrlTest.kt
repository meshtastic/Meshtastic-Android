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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun spec(vararg tiles: String, minZoom: Int = 0, maxZoom: Int = 19) =
    RasterTileSpec(tiles = tiles.toList(), minZoom = minZoom, maxZoom = maxZoom)

class TileUrlTest {
    @Test
    fun `placeholders are filled by name so an ArcGIS z-y-x template survives`() {
        val url = spec("https://example.org/tile/{z}/{y}/{x}.jpg").tileUrl(x = 3, y = 5, zoom = 7)

        assertEquals("https://example.org/tile/7/5/3.jpg", url)
    }

    @Test
    fun `placeholder case does not matter`() {
        val url = spec("https://example.org/{Z}/{X}/{Y}.png").tileUrl(x = 1, y = 2, zoom = 3)

        assertEquals("https://example.org/3/1/2.png", url)
    }

    @Test
    fun `a tile outside the declared zoom range has no url`() {
        val source = spec("https://example.org/{z}/{x}/{y}.png", minZoom = 4, maxZoom = 10)

        assertNull(source.tileUrl(x = 0, y = 0, zoom = 3))
        assertNull(source.tileUrl(x = 0, y = 0, zoom = 11))
        assertTrue(source.tileUrl(x = 0, y = 0, zoom = 4) != null)
    }

    @Test
    fun `a template we cannot finish is refused rather than requested with its braces`() {
        // The stored source is the user's; an unfilled {apiKey} must not reach the network looking like a server fault.
        assertNull(spec("https://example.org/{z}/{x}/{y}.png?token={apiKey}").tileUrl(x = 0, y = 0, zoom = 0))
    }

    @Test
    fun `a source with no tile template has no url`() {
        assertNull(spec().tileUrl(x = 0, y = 0, zoom = 0))
    }

    @Test
    fun `subdomains rotate so neighbouring tiles do not all hit one host`() {
        val source = spec("https://{s}.example.org/{z}/{x}/{y}.png")
        val hosts = (0..2).map { x -> source.tileUrl(x = x, y = 0, zoom = 1)?.substringAfter("https://")?.take(1) }

        assertEquals(listOf("a", "b", "c"), hosts)
    }

    @Test
    fun `a negative coordinate still picks a valid subdomain instead of throwing`() {
        assertTrue(spec("https://{s}.example.org/{z}/{x}/{y}.png").tileUrl(x = -1, y = 0, zoom = 1) != null)
    }
}

class WebMercatorBboxTest {
    @Test
    fun `zoom zero spans the whole projected world`() {
        assertEquals(
            "-20037508.343,-20037508.343,20037508.343,20037508.343",
            webMercatorTileBbox(x = 0, y = 0, zoom = 0),
        )
    }

    @Test
    fun `the top-left tile of zoom one covers the north-west quadrant`() {
        assertEquals("-20037508.343,0.000,0.000,20037508.343", webMercatorTileBbox(x = 0, y = 0, zoom = 1))
    }

    @Test
    fun `bounds are never written in exponent notation`() {
        // Kotlin renders these magnitudes as 2.0037508343E7 by default and a WMS server rejects that outright.
        val bbox = webMercatorTileBbox(x = 511, y = 340, zoom = 10)

        assertTrue('E' !in bbox && 'e' !in bbox, bbox)
    }

    @Test
    fun `a WMS overlay resolves its bbox placeholder into four bounds`() {
        val url = MapTileCatalogue.NoaaRadar.spec.tileUrl(x = 163, y = 395, zoom = 10)

        assertNotNull(url)
        assertTrue('{' !in url, url)
        assertEquals(4, url.substringAfter("BBOX=").substringBefore("&").split(",").size)
    }
}

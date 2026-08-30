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

import com.google.maps.android.data.parser.geojson.GeoJsonParser
import com.google.maps.android.data.renderer.mapper.toLayer
import com.google.maps.android.data.renderer.model.LineStyle
import com.google.maps.android.data.renderer.model.PointStyle
import com.google.maps.android.data.renderer.model.PolygonStyle
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import android.graphics.Color as AndroidColor

/**
 * The Google map fades an imported layer by scaling the alpha its features already carry, because maps-utils draws
 * plain map objects rather than styled tiles — there is no layer-wide opacity to set.
 */
@RunWith(RobolectricTestRunner::class)
class MapLayerOpacityTest {

    private fun layerFrom(geoJson: String) =
        assertNotNull(GeoJsonParser().parse(geoJson.byteInputStream())?.toLayer()?.applySimpleStyleSpec())

    private val polygon =
        """
        {"type":"FeatureCollection","features":[{"type":"Feature",
          "properties":{"fill":"#ff0000","fill-opacity":"0.8","stroke":"#0000ff"},
          "geometry":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,0]]]}}]}
        """
            .trimIndent()

    private val line =
        """
        {"type":"FeatureCollection","features":[{"type":"Feature",
          "properties":{"stroke":"#00ff00"},
          "geometry":{"type":"LineString","coordinates":[[0,0],[1,1]]}}]}
        """
            .trimIndent()

    @Test
    fun `a fully opaque layer is left exactly as it was`() {
        val original = layerFrom(polygon)

        assertEquals(original, original.scaledByOpacity(1f))
    }

    @Test
    fun `a polygon's fill and stroke alpha are both scaled`() {
        val faded = layerFrom(polygon).scaledByOpacity(0.5f)

        val style = assertNotNull(faded.features.single().style as? PolygonStyle)
        // fill-opacity 0.8 → 204, halved.
        assertEquals(102, AndroidColor.alpha(style.fillColor))
        assertEquals(128, AndroidColor.alpha(style.strokeColor))
    }

    @Test
    fun `a line's alpha is scaled`() {
        val faded = layerFrom(line).scaledByOpacity(0.25f)

        val style = assertNotNull(faded.features.single().style as? LineStyle)
        assertEquals(64, AndroidColor.alpha(style.color))
    }

    @Test
    fun `a point's alpha is scaled too`() {
        // A point-only KML stayed fully visible at 0% while this was skipped: PointStyle does carry an ARGB colour,
        // which the renderer turns into the marker's alpha.
        val point =
            """
            {"type":"FeatureCollection","features":[{"type":"Feature",
              "properties":{"icon-url":"https://example.org/tower.png"},
              "geometry":{"type":"Point","coordinates":[0,0]}}]}
            """
                .trimIndent()

        val faded = layerFrom(point).scaledByOpacity(0.5f)

        val style = assertNotNull(faded.features.single().style as? PointStyle)
        assertEquals(128, AndroidColor.alpha(style.color))
    }

    @Test
    fun `scaling alpha leaves the colour itself alone`() {
        val faded = layerFrom(line).scaledByOpacity(0.5f)

        val style = assertNotNull(faded.features.single().style as? LineStyle)
        assertEquals(0, AndroidColor.red(style.color))
        assertEquals(255, AndroidColor.green(style.color))
        assertEquals(0, AndroidColor.blue(style.color))
    }
}

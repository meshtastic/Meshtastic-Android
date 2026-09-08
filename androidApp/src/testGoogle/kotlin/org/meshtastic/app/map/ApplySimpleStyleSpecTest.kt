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
import com.google.maps.android.data.renderer.model.Feature
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
 * What the app's own styling pass adds on top of the maps-utils GeoJSON mapper.
 *
 * The mapper attaches no style at all unless a feature carries one of its own simplestyle keys, so everything below is
 * a case where leaning on it alone would silently change how an import draws.
 */
@RunWith(RobolectricTestRunner::class)
class ApplySimpleStyleSpecTest {

    private fun featureFrom(geoJson: String): Feature =
        assertNotNull(GeoJsonParser().parse(geoJson.byteInputStream())?.toLayer()?.applySimpleStyleSpec())
            .features
            .single()

    private fun polygon(properties: String) = featureFrom(
        """
        {"type":"FeatureCollection","features":[{"type":"Feature",
          "properties":{$properties},
          "geometry":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,0]]]}}]}
        """
            .trimIndent(),
    )

    private fun lineString(properties: String) = featureFrom(
        """
        {"type":"FeatureCollection","features":[{"type":"Feature",
          "properties":{$properties},
          "geometry":{"type":"LineString","coordinates":[[0,0],[1,1]]}}]}
        """
            .trimIndent(),
    )

    private val Feature.polygonStyle: PolygonStyle
        get() = assertNotNull(style as? PolygonStyle, "expected a PolygonStyle, got $style")

    private val Feature.lineStyle: LineStyle
        get() = assertNotNull(style as? LineStyle, "expected a LineStyle, got $style")

    // region Site Planner's legacy `color` key — the mapper reads only `fill`/`stroke`.

    @Test
    fun `a polygon falls back to the legacy color property for both fill and stroke`() {
        val style = polygon(""""color":"#00ff00"""").polygonStyle

        assertEquals(0x00, AndroidColor.red(style.fillColor))
        assertEquals(0xFF, AndroidColor.green(style.fillColor))
        assertEquals(0xFF00FF00.toInt(), style.strokeColor)
    }

    @Test
    fun `a line falls back to the legacy color property`() {
        assertEquals(0xFF00FF00.toInt(), lineString(""""color":"#00ff00"""").lineStyle.color)
    }

    // endregion

    // region rgb()/rgba() — the mapper's parser rejects functional notation and drops the style entirely.

    @Test
    fun `rgb colors are parsed`() {
        val fill = polygon(""""color":"rgb(255, 128, 0)"""").polygonStyle.fillColor

        assertEquals(255, AndroidColor.red(fill))
        assertEquals(128, AndroidColor.green(fill))
        assertEquals(0, AndroidColor.blue(fill))
    }

    @Test
    fun `an rgba alpha survives when no fill-opacity is given`() {
        // The colour's own alpha wins over the default; 0.35 would give 89.
        assertEquals(128, AndroidColor.alpha(polygon(""""fill":"rgba(255, 0, 0, 0.5)"""").polygonStyle.fillColor))
    }

    // endregion

    // region The default fill opacity that makes stacked coverage bands read as a gradient.

    @Test
    fun `an opaque fill with no fill-opacity gets the default gradient opacity`() {
        assertEquals(89, AndroidColor.alpha(polygon(""""fill":"#ff0000"""").polygonStyle.fillColor))
    }

    @Test
    fun `an explicit numeric fill-opacity wins over the default`() {
        // Site Planner writes fill-opacity as a JSON number, not a string; the parser stringifies it on the way in.
        val style = polygon(""""fill":"#ff0000","fill-opacity":0.8""").polygonStyle

        assertEquals(204, AndroidColor.alpha(style.fillColor))
    }

    // endregion

    // region stroke-opacity on lines — the mapper applies it to polygons only, and the KML converter emits it on
    // every styled line.

    @Test
    fun `stroke-opacity fades a line`() {
        val style = lineString(""""stroke":"#ff0000","stroke-opacity":"0.5"""").lineStyle

        assertEquals(128, AndroidColor.alpha(style.color))
        assertEquals(0xFF, AndroidColor.red(style.color))
    }

    // endregion

    // region Defaults: the mapper's are 1px strokes and no style at all for a bare geometry.

    @Test
    fun `imported lines and polygons default to a 2px stroke`() {
        assertEquals(2f, lineString(""""stroke":"#ff0000"""").lineStyle.width)
        assertEquals(2f, polygon(""""fill":"#ff0000"""").polygonStyle.strokeWidth)
    }

    @Test
    fun `a feature carrying no style properties still gets a style`() {
        // Without one, the Maps SDK's own defaults draw the shape and the opacity slider cannot fade it.
        val style = polygon("").polygonStyle

        assertEquals(AndroidColor.TRANSPARENT, style.fillColor)
        assertEquals(AndroidColor.BLACK, style.strokeColor)
        assertEquals(2f, style.strokeWidth)
    }

    @Test
    fun `an unparseable color falls back to the defaults rather than throwing`() {
        val style = polygon(""""fill":"not-a-color"""").polygonStyle

        assertEquals(AndroidColor.TRANSPARENT, style.fillColor)
        assertEquals(AndroidColor.BLACK, style.strokeColor)
    }

    // endregion

    // region Geometry classification.

    @Test
    fun `a MultiPolygon is styled as a polygon, not a line`() {
        val style =
            featureFrom(
                """
                    {"type":"FeatureCollection","features":[{"type":"Feature",
                      "properties":{"fill":"#ff0000","stroke":"#0000ff"},
                      "geometry":{"type":"MultiPolygon","coordinates":[[[[0,0],[1,0],[1,1],[0,0]]]]}}]}
                    """
                    .trimIndent(),
            )
                .polygonStyle

        assertEquals(89, AndroidColor.alpha(style.fillColor))
    }

    @Test
    fun `a MultiLineString is styled as a line`() {
        val style =
            featureFrom(
                """
                    {"type":"FeatureCollection","features":[{"type":"Feature",
                      "properties":{"stroke":"#ff0000"},
                      "geometry":{"type":"MultiLineString","coordinates":[[[0,0],[1,1]]]}}]}
                    """
                    .trimIndent(),
            )
                .lineStyle

        assertEquals(0xFFFF0000.toInt(), style.color)
    }

    @Test
    fun `a MultiPoint keeps its icon instead of being styled as a line`() {
        // The mapper routes any non-polygonal multi-geometry into its line branch, which would lose the icon.
        val feature =
            featureFrom(
                """
                {"type":"FeatureCollection","features":[{"type":"Feature",
                  "properties":{"icon-url":"files/tower.png","stroke":"#ff0000"},
                  "geometry":{"type":"MultiPoint","coordinates":[[0,0],[1,1]]}}]}
                """
                    .trimIndent(),
            )

        assertEquals("files/tower.png", assertNotNull(feature.style as? PointStyle).iconUrl)
    }

    @Test
    fun `a mixed geometry collection is styled as a line`() {
        // Same rule the mapper uses — anything not wholly polygonal is a line — so the app's extras and the opacity
        // slider reach it too.
        val style =
            featureFrom(
                """
                    {"type":"FeatureCollection","features":[{"type":"Feature",
                      "properties":{"stroke":"#ff0000","stroke-opacity":"0.5"},
                      "geometry":{"type":"GeometryCollection","geometries":[
                        {"type":"Point","coordinates":[0,0]},
                        {"type":"LineString","coordinates":[[0,0],[1,1]]}]}}]}
                    """
                    .trimIndent(),
            )
                .lineStyle

        assertEquals(128, AndroidColor.alpha(style.color))
        assertEquals(2f, style.width)
    }

    @Test
    fun `an unstyled mixed geometry collection draws like every other unstyled line`() {
        // The mapper leaves this one styleless, which drew it at the Maps SDK's own 10px default and put it out of
        // the opacity slider's reach. It now matches a bare LineString.
        val style =
            featureFrom(
                """
                    {"type":"FeatureCollection","features":[{"type":"Feature",
                      "properties":{},
                      "geometry":{"type":"GeometryCollection","geometries":[
                        {"type":"Point","coordinates":[0,0]},
                        {"type":"LineString","coordinates":[[0,0],[1,1]]}]}}]}
                    """
                    .trimIndent(),
            )
                .lineStyle

        assertEquals(AndroidColor.BLACK, style.color)
        assertEquals(2f, style.width)
    }

    @Test
    fun `a collection nesting a MultiPolygon is styled as a polygon`() {
        // Classification recurses, so a collection whose members are all polygonal takes the polygon branch and picks
        // up the legacy `color` fallback and the default fill opacity, the same as a bare Polygon.
        val style =
            featureFrom(
                """
                    {"type":"FeatureCollection","features":[{"type":"Feature",
                      "properties":{"color":"#ff0000"},
                      "geometry":{"type":"GeometryCollection","geometries":[
                        {"type":"MultiPolygon","coordinates":[[[[0,0],[1,0],[1,1],[0,0]]]]}]}}]}
                    """
                    .trimIndent(),
            )
                .polygonStyle

        assertEquals(89, AndroidColor.alpha(style.fillColor))
        assertEquals(0xFF, AndroidColor.red(style.fillColor))
        assertEquals(0xFFFF0000.toInt(), style.strokeColor)
    }

    // endregion
}

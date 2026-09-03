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
import com.google.maps.android.data.renderer.model.PointStyle
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class GoogleMapImportedIconStyleTest {

    private fun pointLayer(geoJson: String) =
        assertNotNull(GeoJsonParser().parse(geoJson.byteInputStream())?.toLayer()?.applySimpleStyleSpec())
            .features
            .single()

    @Test
    fun `relative icon url is preserved for imported geojson points`() {
        val point =
            pointLayer(
                """
                {"type":"FeatureCollection","features":[{"type":"Feature",
                  "properties":{"icon-url":"files/tower.png"},
                  "geometry":{"type":"Point","coordinates":[0,0]}}]}
                """
                    .trimIndent(),
            )

        assertEquals("files/tower.png", (point.style as? PointStyle)?.iconUrl)
    }

    @Test
    fun `remote icon url is stripped from imported geojson points`() {
        val point =
            pointLayer(
                """
                {"type":"FeatureCollection","features":[{"type":"Feature",
                  "properties":{"icon-url":"https://example.org/tower.png"},
                  "geometry":{"type":"Point","coordinates":[0,0]}}]}
                """
                    .trimIndent(),
            )

        assertNull((point.style as? PointStyle)?.iconUrl)
    }
}

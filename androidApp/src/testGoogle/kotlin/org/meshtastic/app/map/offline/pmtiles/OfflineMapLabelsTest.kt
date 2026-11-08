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
package org.meshtastic.app.map.offline.pmtiles

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineMapLabelsTest {

    private val view = LatLngBounds(LatLng(0.0, 0.0), LatLng(8.0, 5.0))

    private fun label(
        name: String,
        lat: Double = 0.5,
        lon: Double = 0.5,
        kind: OfflineMapLabel.Kind = OfflineMapLabel.Kind.City,
        minZoom: Double = 0.0,
        rank: Int = 0,
    ) = OfflineMapLabel(name, LatLng(lat, lon), kind, minZoom, rank)

    private fun point(layer: String, vararg props: Pair<String, Any>, at: LatLng = LatLng(1.0, 1.0)) = OfflineFeature(
        layerName = layer,
        geometryType = VectorTile.GEOM_POINT,
        rings = listOf(listOf(at)),
        properties = mapOf(*props),
    )

    @Test
    fun `ranks by min zoom, then bigger population, then kind, then name`() {
        val labels =
            listOf(
                label("z", minZoom = 5.0),
                label("small", minZoom = 3.0, rank = 1),
                label("big", minZoom = 3.0, rank = 9),
                label("A town", minZoom = 3.0, rank = 1, kind = OfflineMapLabel.Kind.Town),
                label("a", minZoom = 3.0, rank = 1),
            )

        val ranked = OfflineMapLabels.ranked(labels.map(::asFeature), Locale.ENGLISH).map { it.name }

        // "A town" sorts first by name, so it lands after the cities only through the kind comparison.
        assertEquals(listOf("big", "a", "small", "A town", "z"), ranked)
    }

    @Test
    fun `a label off a rotated screen does not claim its cell`() {
        val ranked = listOf(label("offscreen", lat = 0.2, lon = 0.2), label("onscreen", lat = 0.4, lon = 0.4))

        val shown = OfflineMapLabels.visible(ranked, view, zoom = 10.0) { it.latitude > 0.3 }

        assertEquals(listOf("onscreen"), shown.map { it.name })
    }

    @Test
    fun `only labels whose min zoom has been reached show`() {
        val ranked = listOf(label("far", minZoom = 4.0, lat = 1.5), label("near", minZoom = 9.0, lat = 3.5))

        assertEquals(listOf("far"), OfflineMapLabels.visible(ranked, view, zoom = 6.0).map { it.name })
        assertEquals(listOf("far", "near"), OfflineMapLabels.visible(ranked, view, zoom = 9.0).map { it.name })
    }

    @Test
    fun `one label per grid cell keeps the most important`() {
        val ranked = listOf(label("first", lat = 0.2, lon = 0.2), label("second", lat = 0.4, lon = 0.4))
        val other = label("other", lat = 2.5, lon = 3.5)

        val shown = OfflineMapLabels.visible(ranked + other, view, zoom = 10.0)

        assertEquals(listOf("first", "other"), shown.map { it.name })
    }

    @Test
    fun `labels outside the view are dropped`() {
        val ranked = listOf(label("south", lat = -1.0), label("east", lon = 6.0), label("in"))

        assertEquals(listOf("in"), OfflineMapLabels.visible(ranked, view, zoom = 10.0).map { it.name })
    }

    @Test
    fun `a full grid shows 40 labels, one per cell`() {
        val ranked =
            (0 until OfflineMapLabels.ROWS).flatMap { row ->
                (0 until OfflineMapLabels.COLUMNS).map { col -> label("$row-$col", lat = row + 0.5, lon = col + 0.5) }
            } + label("extra", lat = 0.9, lon = 0.9)

        assertEquals(40, OfflineMapLabels.visible(ranked, view, zoom = 10.0).size)
    }

    @Test
    fun `a view across the antimeridian keeps the far side`() {
        val across = LatLngBounds(LatLng(0.0, 175.0), LatLng(8.0, -170.0))
        val ranked = listOf(label("east", lon = 178.0), label("west", lon = -172.0), label("away", lon = 0.0))

        assertEquals(listOf("east", "west"), OfflineMapLabels.visible(ranked, across, zoom = 10.0).map { it.name })
    }

    @Test
    fun `countries are skipped and water names are included`() {
        val features =
            listOf(
                point("places", "kind" to "country", "name" to "Land"),
                point("places", "kind" to "locality", "kind_detail" to "city", "name" to "Town City"),
                point("water", "name" to "Big Lake"),
                point("water", "kind" to "lake"),
                point("roads", "name" to "Main St"),
            )

        val ranked = OfflineMapLabels.ranked(features, Locale.ENGLISH)

        assertEquals(
            setOf("Town City" to OfflineMapLabel.Kind.City, "Big Lake" to OfflineMapLabel.Kind.Water),
            ranked.map { it.name to it.kind }.toSet(),
        )
    }

    @Test
    fun `the same label from two tiles shows once`() {
        val feature = point("places", "kind" to "locality", "name" to "Twice")

        assertEquals(1, OfflineMapLabels.ranked(listOf(feature, feature), Locale.ENGLISH).size)
    }

    @Test
    fun `localized name wins over the local name, script first`() {
        val props = mapOf<String, Any>("name" to "Local", "name:fr" to "Francais", "name:zh-Hant" to "Hant")

        assertEquals("Francais", OfflineMapLabels.localizedName(props, Locale.FRENCH))
        assertEquals("Hant", OfflineMapLabels.localizedName(props, Locale.forLanguageTag("zh-Hant")))
        assertEquals("Local", OfflineMapLabels.localizedName(props, Locale.GERMAN))
    }

    private fun asFeature(label: OfflineMapLabel) = point(
        "places",
        "kind" to "locality",
        "kind_detail" to if (label.kind == OfflineMapLabel.Kind.Town) "town" else "city",
        "name" to label.name,
        "min_zoom" to label.minZoom,
        "population_rank" to label.rank.toDouble(),
        at = label.position,
    )
}

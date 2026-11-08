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
import kotlin.math.floor

/** A place or water name to print on the offline map, from the basemap's `places` and `water` layers. */
internal data class OfflineMapLabel(
    val name: String,
    val position: LatLng,
    val kind: Kind,
    /** The basemap's `min_zoom`: the label shows from this map zoom on. */
    val minZoom: Double,
    /** The basemap's `population_rank`: bigger places first within a zoom. */
    val rank: Int,
) {
    /** In order of importance: a city wins a cell over a town, a town over a lake. */
    enum class Kind {
        Region,
        City,
        Town,
        Water,
        Village,
        Suburb,
        Neighborhood,
    }
}

/** Which of the offline map's names to show: the most important first, at most one per cell of a grid over the view. */
internal object OfflineMapLabels {
    const val COLUMNS = 5
    const val ROWS = 8

    private const val FULL_TURN = 360.0
    private const val MAX_RANK = 1_000

    private val ORDER = compareBy<OfflineMapLabel>({ it.minZoom }, { -it.rank }, { it.kind }, { it.name })

    /** The labels in [features] (points of `places` and `water`), deduplicated across tiles, most important first. */
    fun ranked(features: List<OfflineFeature>, locale: Locale = Locale.getDefault()): List<OfflineMapLabel> =
        features.flatMap { labelsOf(it, locale) }.distinctBy { Triple(it.kind, it.name, it.position) }.sortedWith(ORDER)

    /**
     * From [ranked] labels, the ones meant to show at [zoom], the first in each grid cell over [bounds]. [onScreen]
     * drops labels inside [bounds] but outside a rotated or tilted view before they can claim a cell.
     */
    fun visible(
        ranked: List<OfflineMapLabel>,
        bounds: LatLngBounds,
        zoom: Double,
        onScreen: (LatLng) -> Boolean = { true },
    ): List<OfflineMapLabel> {
        val minLat = bounds.southwest.latitude
        val minLon = bounds.southwest.longitude
        val cellLat = (bounds.northeast.latitude - minLat) / ROWS
        val cellLon = wrap(bounds.northeast.longitude - minLon) / COLUMNS
        if (cellLat <= 0.0 || cellLon <= 0.0) return emptyList()
        val taken = mutableSetOf<Int>()
        val result = mutableListOf<OfflineMapLabel>()
        for (label in ranked.filter { it.minZoom <= zoom && onScreen(it.position) }) {
            val row = floor((label.position.latitude - minLat) / cellLat).toInt()
            val column = floor(wrap(label.position.longitude - minLon) / cellLon).toInt()
            if (row in 0 until ROWS && column in 0 until COLUMNS && taken.add(row * COLUMNS + column)) {
                result += label
            }
        }
        return result
    }

    /** The name in the app's language when the basemap has it (`name:fr`, `name:zh-Hant`), else the local name. */
    fun localizedName(properties: Map<String, Any>, locale: Locale): String? {
        fun text(key: String) = (properties[key] as? String)?.takeIf { it.isNotEmpty() }
        val language = locale.language.takeIf { it.isNotEmpty() }
        val script = locale.script.takeIf { it.isNotEmpty() }
        val scripted = language?.let { l -> script?.let { text("name:$l-$it") } }
        val plain = language?.let { text("name:$it") }
        return scripted ?: plain ?: text("name")
    }

    /** The label kind for a `places` feature; null for countries, which are too big for the maps here. */
    fun placeKind(properties: Map<String, Any>): OfflineMapLabel.Kind? {
        val kind = OfflineMapStyle.kind(properties)
        val detail = OfflineMapStyle.kindDetail(properties) ?: properties["place"] as? String
        return when (kind) {
            "region" -> OfflineMapLabel.Kind.Region

            "locality" ->
                when (detail) {
                    "city" -> OfflineMapLabel.Kind.City
                    "town" -> OfflineMapLabel.Kind.Town
                    else -> OfflineMapLabel.Kind.Village
                }

            "macrohood" -> OfflineMapLabel.Kind.Suburb

            "neighbourhood" -> OfflineMapLabel.Kind.Neighborhood

            else -> null
        }
    }

    private fun labelsOf(feature: OfflineFeature, locale: Locale): List<OfflineMapLabel> {
        val kind =
            when (feature.layerName) {
                "places" -> placeKind(feature.properties)
                "water" -> OfflineMapLabel.Kind.Water
                else -> null
            }
        val name = localizedName(feature.properties, locale)
        if (kind == null || name == null || feature.geometryType != VectorTile.GEOM_POINT) return emptyList()
        val minZoom = OfflineMapStyle.minZoom(feature.properties)
        val rank =
            OfflineMapStyle.number(feature.properties["population_rank"] ?: feature.properties["pmap:population_rank"])
                ?.coerceIn(-MAX_RANK.toDouble(), MAX_RANK.toDouble())
                ?.toInt() ?: 0
        return feature.rings.flatten().map { OfflineMapLabel(name, it, kind, minZoom, rank) }
    }

    /** Degrees east of the view's west edge, wrapped so a view across ±180° keeps the far side. */
    private fun wrap(degrees: Double): Double {
        val east = degrees % FULL_TURN
        return if (east < 0) east + FULL_TURN else east
    }
}

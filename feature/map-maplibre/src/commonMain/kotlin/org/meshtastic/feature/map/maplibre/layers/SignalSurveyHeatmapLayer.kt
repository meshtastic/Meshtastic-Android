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
package org.meshtastic.feature.map.maplibre.layers

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.exponential
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.heatmapDensity
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.layers.HeatmapLayer
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.ui.component.Quality
import org.meshtastic.feature.map.maplibre.geojson.rememberFeatureSource
import org.meshtastic.feature.map.survey.SurveyCellUi

private const val KEY_QUALITY = "quality"
private const val HEAT_OPACITY = 0.75f
private const val FAINT_ALPHA = 0.35f
private const val MID_ALPHA = 0.6f
private const val FULL_ALPHA = 0.8f
private const val CENTER = 0.5
private const val FAINT_DENSITY = 0.15
private const val DOUBLING = 2f

// MapLibre zoom levels (512 px tiles) the heat radius is pinned at.
private const val CITY_ZOOM = 10
private const val STREET_ZOOM = 15
private const val BLOCK_ZOOM = 19

/**
 * The signal survey as a heatmap: one layer per signal band, each fading from clear to that band's color, drawn worst
 * first so good coverage reads on top where bands meet. Heat is the density of 50 m squares in that band, not of raw
 * readings, so color answers "how good" and the glow answers "how much of the route", never "how long I stood here".
 */
@Suppress("SpreadOperator") // interpolate() only takes its stops as varargs
@Composable
internal fun SignalSurveyHeatmapLayer(points: List<SurveyCellUi>, colors: Map<Quality, Color>) {
    val source = rememberFeatureSource(points) { points.toPointCollection() }
    // The radius doubles with each zoom level, as the 50 m spacing between points does on screen, so the route stays
    // one continuous band instead of a string of beads.
    val radius =
        interpolate(
            exponential(DOUBLING),
            zoom(),
            CITY_ZOOM to const(4.dp),
            STREET_ZOOM to const(36.dp),
            BLOCK_ZOOM to const(500.dp),
        )
    Quality.entries.reversed().forEach { quality ->
        val color = colors[quality] ?: return@forEach
        HeatmapLayer(
            id = "signal-survey-heat-${quality.name.lowercase()}",
            source = source,
            filter = feature[KEY_QUALITY].asString() eq const(quality.name),
            color =
            interpolate(
                linear(),
                heatmapDensity(),
                0 to const(Color.Transparent),
                FAINT_DENSITY to const(color.copy(alpha = FAINT_ALPHA)),
                CENTER to const(color.copy(alpha = MID_ALPHA)),
                1 to const(color.copy(alpha = FULL_ALPHA)),
            ),
            radius = radius,
            opacity = const(HEAT_OPACITY),
        )
    }
}

private fun List<SurveyCellUi>.toPointCollection(): FeatureCollection<Point, JsonObject?> = FeatureCollection(
    map { item ->
        val cell = item.cell
        Feature<Point, JsonObject?>(
            geometry =
            Point(
                Position(
                    longitude = (cell.west + cell.east) * CENTER,
                    latitude = (cell.south + cell.north) * CENTER,
                ),
            ),
            properties = buildJsonObject { put(KEY_QUALITY, item.quality.name) },
        )
    },
)

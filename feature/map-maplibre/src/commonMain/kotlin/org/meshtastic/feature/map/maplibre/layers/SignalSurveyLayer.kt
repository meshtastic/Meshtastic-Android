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
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.FillLayer
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.ui.component.Quality
import org.meshtastic.feature.map.maplibre.geojson.rememberFeatureSource
import org.meshtastic.feature.map.survey.SurveyCellUi

private const val KEY_ID = "id"
private const val KEY_COLOR = "color"
private const val SURVEY_FILL_OPACITY = 0.6f
private const val RGB_MASK = 0xFFFFFF
private const val HEX = 16
private const val HEX_DIGITS = 6

/**
 * The signal survey's squares, colored by signal quality and tappable. No outline: neighbors share edges, and a stroke
 * on each would draw a grid over what should read as one band along the route.
 */
@Composable
internal fun SignalSurveyLayer(cells: List<SurveyCellUi>, colors: Map<Quality, Color>, onCellClick: (String) -> Unit) {
    val source = rememberFeatureSource(cells, colors) { cells.toFeatureCollection(colors) }
    FillLayer(
        id = "signal-survey-fill",
        source = source,
        color = feature[KEY_COLOR].convertToColor(const(Color.Gray)),
        opacity = const(SURVEY_FILL_OPACITY),
        onClick = { features ->
            features.firstOrNull()?.properties?.get(KEY_ID)?.let { id ->
                onCellClick(id.jsonPrimitive.content)
                ClickResult.Consume
            } ?: ClickResult.Pass
        },
    )
}

private fun List<SurveyCellUi>.toFeatureCollection(
    colors: Map<Quality, Color>,
): FeatureCollection<Geometry, JsonObject?> = FeatureCollection(
    map { item ->
        val cell = item.cell
        val ring =
            listOf(
                Position(longitude = cell.west, latitude = cell.south),
                Position(longitude = cell.west, latitude = cell.north),
                Position(longitude = cell.east, latitude = cell.north),
                Position(longitude = cell.east, latitude = cell.south),
                Position(longitude = cell.west, latitude = cell.south),
            )
        Feature<Geometry, JsonObject?>(
            geometry = Polygon(listOf(ring)),
            properties =
            buildJsonObject {
                put(KEY_ID, item.id)
                put(KEY_COLOR, (colors[item.quality] ?: Color.Gray).toCssHex())
            },
        )
    },
)

private fun Color.toCssHex(): String = "#" + (toArgb() and RGB_MASK).toString(HEX).padStart(HEX_DIGITS, '0')

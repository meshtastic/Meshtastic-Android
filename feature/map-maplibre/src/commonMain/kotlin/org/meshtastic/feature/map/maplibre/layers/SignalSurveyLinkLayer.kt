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
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.layers.LineLayer
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.ui.component.Quality
import org.meshtastic.feature.map.maplibre.geojson.rememberFeatureSource
import org.meshtastic.feature.map.survey.SurveyLink

private const val KEY_COLOR = "color"
private const val RGB_MASK = 0xFFFFFF
private const val HEX = 16
private const val HEX_DIGITS = 6

/** Lines from the tapped survey square to each node heard directly there, in that reception's quality color. */
@Composable
internal fun SignalSurveyLinkLayer(links: List<SurveyLink>, colors: Map<Quality, Color>) {
    val source =
        rememberFeatureSource(links, colors) {
            FeatureCollection(
                links.map { link ->
                    Feature<LineString, JsonObject?>(
                        geometry =
                        LineString(
                            listOf(
                                Position(longitude = link.fromLongitude, latitude = link.fromLatitude),
                                Position(longitude = link.toLongitude, latitude = link.toLatitude),
                            ),
                        ),
                        properties =
                        buildJsonObject {
                            val color = colors[link.quality] ?: Color.Gray
                            put(
                                KEY_COLOR,
                                "#" + (color.toArgb() and RGB_MASK).toString(HEX).padStart(HEX_DIGITS, '0'),
                            )
                        },
                    )
                },
            )
        }
    // A dark casing under each line keeps it readable over the squares, which use the same colors.
    LineLayer(id = "signal-survey-link-casing", source = source, color = const(Color.Black), width = const(5.dp))
    LineLayer(
        id = "signal-survey-link",
        source = source,
        color = feature[KEY_COLOR].convertToColor(const(Color.Gray)),
        width = const(3.dp),
    )
}

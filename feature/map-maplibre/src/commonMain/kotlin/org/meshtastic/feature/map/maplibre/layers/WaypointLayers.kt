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
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.meshtastic.core.model.DataPacket
import org.meshtastic.feature.map.maplibre.geojson.WaypointFeatureKeys
import org.meshtastic.feature.map.maplibre.geojson.geofencesToFeatureCollection
import org.meshtastic.feature.map.maplibre.geojson.iconGlyph
import org.meshtastic.feature.map.maplibre.geojson.waypointsToFeatureCollection
import org.meshtastic.feature.map.maplibre.style.MapColors

private const val GEOFENCE_FILL_OPACITY = 0.12f

/**
 * Waypoint markers and their geofence zones.
 *
 * Zones are drawn first so a marker is never hidden behind its own boundary fill.
 */
@Composable
internal fun WaypointLayers(waypoints: Collection<DataPacket>, onWaypointClick: (Int) -> Unit) {
    val geofenceSource = rememberGeoJsonSource(data = GeoJsonData.Features(geofencesToFeatureCollection(waypoints)))

    FillLayer(
        id = "geofence-fill",
        source = geofenceSource,
        color = const(MapColors.Highlight),
        opacity = const(GEOFENCE_FILL_OPACITY),
    )

    LineLayer(id = "geofence-outline", source = geofenceSource, color = const(MapColors.Highlight), width = const(2.dp))

    val waypointSource = rememberGeoJsonSource(data = GeoJsonData.Features(waypointsToFeatureCollection(waypoints)))

    // One layer per distinct glyph, rather than one text layer for all of them. A waypoint icon is an arbitrary code
    // point from the mesh, and the basemap's font has no emoji coverage — asking for them as text renders nothing at
    // all, which is what used to happen. MapLibre can only bind one image per layer, so the glyphs are grouped.
    val glyphs = remember(waypoints) { waypoints.mapNotNull { it.waypoint?.icon }.map { iconGlyph(it) }.distinct() }

    glyphs.forEach { glyph ->
        SymbolLayer(
            id = "waypoint-icon-${glyph.codePointsKey()}",
            source = waypointSource,
            filter = feature[WaypointFeatureKeys.ICON].asString() eq const(glyph),
            iconImage = image(rememberGlyphPainter(glyph), DpSize(WAYPOINT_ICON_DP.dp, WAYPOINT_ICON_DP.dp)),
            iconAllowOverlap = const(true),
            onClick = { features ->
                features.firstOrNull()?.properties?.get(WaypointFeatureKeys.WAYPOINT_ID)?.let { id ->
                    onWaypointClick(id.jsonPrimitive.int)
                    ClickResult.Consume
                } ?: ClickResult.Pass
            },
        )
    }

    SymbolLayer(
        id = "waypoint-label",
        source = waypointSource,
        textField = feature[WaypointFeatureKeys.NAME].asString(),
        textFont = const(listOf("Noto Sans Regular")),
        textColor = const(Color.White),
        textHaloColor = const(Color.Black),
        textHaloWidth = const(1.dp),
        textOffset = org.maplibre.compose.expressions.dsl.offset(0f.em, WAYPOINT_LABEL_OFFSET_EM.em),
    )
}

private const val WAYPOINT_ICON_DP = 24
private const val WAYPOINT_LABEL_OFFSET_EM = 1.2f

/** Draws [glyph] — an emoji — as an image MapLibre can use for an icon. */
@Composable
private fun rememberGlyphPainter(glyph: String): Painter {
    val measurer = rememberTextMeasurer()
    val layout =
        remember(glyph, measurer) {
            measurer.measure(AnnotatedString(glyph), TextStyle(fontSize = WAYPOINT_ICON_DP.sp))
        }

    return remember(layout) {
        object : Painter() {
            override val intrinsicSize: Size = Size(layout.size.width.toFloat(), layout.size.height.toFloat())

            override fun DrawScope.onDraw() {
                drawText(layout)
            }
        }
    }
}

/** A stable, id-safe key for a glyph: its code points. Layer ids must not contain arbitrary characters. */
private fun String.codePointsKey(): String = map { it.code.toString(HEX_RADIX) }.joinToString("-")

private const val HEX_RADIX = 16

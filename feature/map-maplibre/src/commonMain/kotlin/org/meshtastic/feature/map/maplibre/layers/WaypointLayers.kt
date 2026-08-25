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
import androidx.compose.ui.unit.em
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.meshtastic.core.model.DataPacket
import org.meshtastic.feature.map.maplibre.geojson.WaypointFeatureKeys
import org.meshtastic.feature.map.maplibre.geojson.geofencesToFeatureCollection
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

    SymbolLayer(
        id = "waypoint-icon",
        source = waypointSource,
        textField = feature[WaypointFeatureKeys.ICON].asString(),
        textSize = const(WAYPOINT_ICON_EM.em),
        textAllowOverlap = const(true),
        onClick = { features ->
            features.firstOrNull()?.properties?.get(WaypointFeatureKeys.WAYPOINT_ID)?.let { id ->
                onWaypointClick(id.jsonPrimitive.int)
                ClickResult.Consume
            } ?: ClickResult.Pass
        },
    )

    SymbolLayer(
        id = "waypoint-label",
        source = waypointSource,
        textField = feature[WaypointFeatureKeys.NAME].asString(),
        textColor = const(Color.White),
        textHaloColor = const(Color.Black),
        textHaloWidth = const(1.dp),
        textOffset = org.maplibre.compose.expressions.dsl.offset(0f.em, WAYPOINT_LABEL_OFFSET_EM.em),
    )
}

private const val WAYPOINT_ICON_EM = 1.5f
private const val WAYPOINT_LABEL_OFFSET_EM = 1.2f

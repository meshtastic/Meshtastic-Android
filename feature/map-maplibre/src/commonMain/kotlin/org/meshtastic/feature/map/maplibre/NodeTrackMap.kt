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
package org.meshtastic.feature.map.maplibre

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.maplibre.component.SecondaryMapControls
import org.meshtastic.feature.map.maplibre.component.TrackFilterMenu
import org.meshtastic.feature.map.maplibre.component.rememberBasemapSelection
import org.meshtastic.feature.map.maplibre.geojson.rememberFeatureSource
import org.meshtastic.feature.map.maplibre.layers.RasterBasemapLayer
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.MapColors
import org.meshtastic.feature.map.maplibre.style.toBaseStyle
import org.meshtastic.feature.map.maplibre.style.zoomRange
import org.maplibre.spatialk.geojson.Position as GeoPosition
import org.meshtastic.proto.Position as ProtoPosition

private const val TRACK_POINT_KEY = "positionTime"

/** A timestamped point on a node's position track. */
private typealias TrackPoint = Pair<GeoPosition, Int>

/**
 * Historic position track for one node, with tap-to-select synchronised to the caller's list.
 *
 * Carries its own controls. The Google flavor reaches this screen through the main map's own composable, so the track
 * map inherits the full toolbar — including the age filter, which is a separate preference from the main map's. Here
 * the track map is its own composable, so the toolbar has to be asked for explicitly.
 *
 * @param customBasemaps The host's own tile sources, for the basemap menu. Supplied by the host because reaching them
 *   means reaching the F-Droid flavor's tile-provider repository, which the desktop host has no equivalent of.
 */
@Composable
fun MapLibreNodeTrackMap(
    destNum: Int,
    positions: List<ProtoPosition>,
    modifier: Modifier = Modifier,
    selectedPositionTime: Int? = null,
    onPositionSelect: ((Int) -> Unit)? = null,
    customBasemaps: @Composable () -> List<Basemap.Raster> = { emptyList() },
) {
    val allPoints = remember(positions) { positions.mapNotNull { it.toTrackPoint() } }
    val cameraState = rememberCameraState()

    // The basemap is a shared preference, so a track opens on whatever the main map is set to. The OSMdroid track map
    // resolved the same preference; hardcoding the default meant picking Dark on the main map and getting Liberty here.
    val basemaps = rememberBasemapSelection(customBasemaps())

    val viewModel: SharedMapViewModel = koinViewModel()
    val filterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val trackFilter = filterState.lastHeardTrackFilter
    val points = remember(allPoints, trackFilter) { allPoints.olderThanCutoffRemoved(trackFilter) }

    // Frame the whole track once it is known. Without this the camera opens at (0, 0) — the black-ocean start the
    // OSMdroid track map suffered from. Keyed on the unfiltered track so tightening the filter does not yank the
    // camera around under the user.
    LaunchedEffect(allPoints.size) {
        if (allPoints.isNotEmpty()) {
            cameraState.position = CameraPosition(target = allPoints.center(), zoom = DETAIL_ZOOM)
        }
    }

    if (allPoints.isEmpty()) return

    // The map and its toolbar stay up even when the filter empties the track — otherwise the control that emptied it
    // disappears along with the points, leaving no way back.
    Box(modifier = modifier) {
        MaplibreMap(
            baseStyle = basemaps.current.toBaseStyle(),
            cameraState = cameraState,
            modifier = Modifier.fillMaxSize(),
            options = SecondaryMapOptions,
            zoomRange = basemaps.current.zoomRange(),
        ) {
            (basemaps.current as? Basemap.Raster)?.let { RasterBasemapLayer(it) }

            // A LineString needs two coordinates; a filter tight enough to leave one point would otherwise throw.
            if (points.size > 1) TrackLineLayer(destNum = destNum, points = points)
            if (points.isNotEmpty()) {
                TrackPointLayer(points = points, onPositionSelect = onPositionSelect)
                selectedPositionTime?.let { selected ->
                    SelectedTrackPointLayer(points = points, selectedTime = selected)
                }
            }
        }
        SecondaryMapControls(
            cameraState = cameraState,
            basemaps = basemaps,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = TOOLBAR_INSET.dp),
            filterMenu = { expanded, onDismissRequest ->
                TrackFilterMenu(expanded = expanded, onDismissRequest = onDismissRequest)
            },
        )
    }
}

private fun ProtoPosition.toTrackPoint(): TrackPoint? {
    val latitude = (latitude_i ?: 0) * DEG_SCALE
    val longitude = (longitude_i ?: 0) * DEG_SCALE
    return if (latitude == 0.0 && longitude == 0.0) {
        null
    } else {
        GeoPosition(longitude = longitude, latitude = latitude) to (time ?: 0)
    }
}

/**
 * Drops points recorded longer ago than [filter] allows.
 *
 * Matches the Google flavor: the cutoff is the position's own timestamp, not when the node was last heard from. A track
 * is a history, so filtering it by the node's liveness would show all of it or none of it.
 */
private fun List<TrackPoint>.olderThanCutoffRemoved(filter: LastHeardFilter): List<TrackPoint> {
    if (filter == LastHeardFilter.Any) return this
    val cutoff = nowSeconds - filter.seconds
    return filter { (_, time) -> time > cutoff }
}

private fun List<TrackPoint>.center(): GeoPosition {
    val latitudes = map { it.first.latitude }
    val longitudes = map { it.first.longitude }
    return GeoPosition(
        longitude = (longitudes.min() + longitudes.max()) / 2,
        latitude = (latitudes.min() + latitudes.max()) / 2,
    )
}

private fun pointFeatures(points: List<TrackPoint>) = FeatureCollection(
    points.map { (position, time) ->
        Feature<Point, JsonObject?>(
            geometry = Point(position),
            properties = buildJsonObject { put(TRACK_POINT_KEY, time) },
        )
    },
)

@Composable
private fun TrackLineLayer(destNum: Int, points: List<TrackPoint>) {
    val source =
        rememberFeatureSource(
            FeatureCollection(
                listOf(
                    Feature<LineString, JsonObject?>(
                        geometry = LineString(points.map { it.first }),
                        properties = buildJsonObject { put("destNum", destNum) },
                    ),
                ),
            ),
        )
    LineLayer(
        id = "track-line",
        source = source,
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
        color = const(MapColors.RouteForward),
        width = const(3.dp),
    )
}

@Composable
private fun TrackPointLayer(points: List<TrackPoint>, onPositionSelect: ((Int) -> Unit)?) {
    val source = rememberFeatureSource(pointFeatures(points))
    CircleLayer(
        id = "track-points",
        source = source,
        color = const(Color.White),
        radius = const(5.dp),
        strokeColor = const(MapColors.RouteForward),
        strokeWidth = const(2.dp),
        onClick = { features ->
            features.firstOrNull()?.properties?.get(TRACK_POINT_KEY)?.let { time ->
                onPositionSelect?.invoke(time.jsonPrimitive.int)
                ClickResult.Consume
            } ?: ClickResult.Pass
        },
    )
}

@Composable
private fun SelectedTrackPointLayer(points: List<TrackPoint>, selectedTime: Int) {
    val source = rememberFeatureSource(pointFeatures(points.filter { it.second == selectedTime }))
    CircleLayer(
        id = "track-selected",
        source = source,
        color = const(MapColors.Highlight),
        radius = const(8.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
    )
}

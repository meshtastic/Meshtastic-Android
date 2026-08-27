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
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.maplibre.component.MapZoom
import org.meshtastic.feature.map.maplibre.component.SecondaryMapControls
import org.meshtastic.feature.map.maplibre.component.TrackFilterMenu
import org.meshtastic.feature.map.maplibre.component.TrackPointCard
import org.meshtastic.feature.map.maplibre.component.rememberBasemapSelection
import org.meshtastic.feature.map.maplibre.geojson.NodeFeatureKeys
import org.meshtastic.feature.map.maplibre.geojson.featureValue
import org.meshtastic.feature.map.maplibre.geojson.rememberFeatureSource
import org.meshtastic.feature.map.maplibre.geojson.toNodeChip
import org.meshtastic.feature.map.maplibre.layers.NodeChipLayer
import org.meshtastic.feature.map.maplibre.layers.RasterBasemapLayer
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.MapColors
import org.meshtastic.feature.map.maplibre.style.toBaseStyle
import org.meshtastic.feature.map.maplibre.style.zoomRange
import org.maplibre.spatialk.geojson.Position as GeoPosition
import org.meshtastic.proto.Position as ProtoPosition

private const val TRACK_POINT_KEY = "positionTime"

/** How far along the track a point sits: 0 for the oldest, 1 for the newest. Drives the age fade. */
private const val TRACK_AGE_KEY = "age"

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
@Suppress("LongMethod")
fun MapLibreNodeTrackMap(
    destNum: Int,
    positions: List<ProtoPosition>,
    modifier: Modifier = Modifier,
    selectedPositionTime: Int? = null,
    onPositionSelect: ((Int) -> Unit)? = null,
    customBasemaps: @Composable () -> List<Basemap.Raster> = { emptyList() },
) {
    // Oldest first, as the Google flavor sorts its own track. Everything downstream reads order as age: the gradient
    // runs from the start of the line, the fade runs from index 0, and the chip goes on the last point. The view model
    // hands these over newest-first for its list, so an unsorted track drew the whole thing backwards.
    val allPoints = remember(positions) { positions.mapNotNull { it.toTrackPoint() }.sortedBy { it.second } }
    val cameraState = rememberCameraState()

    // The basemap is a shared preference, so a track opens on whatever the main map is set to. The OSMdroid track map
    // resolved the same preference; hardcoding the default meant picking Dark on the main map and getting Liberty here.
    val basemaps = rememberBasemapSelection(customBasemaps())

    val viewModel: SharedMapViewModel = koinViewModel()
    val filterState by viewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val displayUnits by viewModel.displayUnits.collectAsStateWithLifecycle()

    val node = remember(nodes, destNum) { nodes.firstOrNull { it.num == destNum } }
    val trackFilter = filterState.lastHeardTrackFilter
    val points = remember(allPoints, trackFilter) { allPoints.olderThanCutoffRemoved(trackFilter) }

    // Frame the whole track, not just its midpoint: a fixed zoom on the centre cropped long tracks at both ends. Keyed
    // on the unfiltered track so tightening the filter does not yank the camera around under the user, and gated on a
    // viewport because fitting a bounding box needs one — before the map reports its size the fit lands on a default.
    val hasViewport = cameraState.viewport != null
    LaunchedEffect(allPoints.size, hasViewport) {
        if (hasViewport && allPoints.isNotEmpty()) {
            positionsBoundingBox(allPoints.map { it.first })?.let { box ->
                cameraState.jumpTo(boundingBox = box, padding = SecondaryMapFitPadding)
            }
        }
    }

    if (allPoints.isEmpty()) return

    // The colour the rest of the app draws this node in. The track used to be a flat blue, which said nothing about
    // whose track it was — the Google flavor fades the node's own colour along it, and so does this now.
    val trackColor = node?.let { Color(it.colors.second) } ?: MapColors.Slate

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
            if (points.size > 1) TrackLineLayer(points = points, color = trackColor)
            if (points.isNotEmpty()) {
                TrackPointLayer(points = points, color = trackColor, onPositionSelect = onPositionSelect)
                selectedPositionTime?.let { selected ->
                    SelectedTrackPointLayer(points = points, selectedTime = selected)
                }
                // The newest position gets the node's chip, as it does on the Google map: the head of the track is
                // where the node is now, and that is the one point worth naming.
                if (node != null) NewestPositionChip(node = node, newest = points.last())
            }
        }
        MapZoom(cameraState = cameraState, basemap = basemaps.current)
        SecondaryMapControls(
            cameraState = cameraState,
            basemaps = basemaps,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = TOOLBAR_INSET.dp),
            filterMenu = { expanded, onDismissRequest ->
                TrackFilterMenu(expanded = expanded, onDismissRequest = onDismissRequest)
            },
        )

        // What the Google flavor puts in a marker info window. MapLibre draws markers as layer features, which have no
        // info window, so the detail for the selected point goes at the foot of the map instead.
        TrackPointCard(
            // Guarded on the selection rather than compared straight through: a position whose `time` is absent is
            // also null, so an unselected map matched it and the card appeared for a point nobody tapped.
            position = selectedPositionTime?.let { selected -> positions.firstOrNull { it.time == selected } },
            displayUnits = displayUnits,
            // Clear of the logo and attribution along the bottom edge, which the styles are licensed on condition of
            // showing. See MeshMapOrnaments.
            modifier =
            Modifier.align(Alignment.BottomStart)
                .padding(start = CARD_INSET.dp, end = CARD_INSET.dp, bottom = ORNAMENT_CLEARANCE.dp),
        )
    }
}

private fun ProtoPosition.toTrackPoint(): TrackPoint? {
    // Both axes required — one absent ordinate substituted as 0 dragged the track to the equator or the prime
    // meridian and drew a leg there that the node never travelled.
    val latitudeI = latitude_i
    val longitudeI = longitude_i
    if (latitudeI == null || longitudeI == null) return null
    val latitude = latitudeI * DEG_SCALE
    val longitude = longitudeI * DEG_SCALE
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

private fun pointFeatures(points: List<TrackPoint>) = FeatureCollection(
    points.mapIndexed { index, (position, time) ->
        Feature<Point, JsonObject?>(
            geometry = Point(position),
            properties =
            buildJsonObject {
                put(TRACK_POINT_KEY, time)
                put(TRACK_AGE_KEY, if (points.size > 1) index.toDouble() / (points.size - 1) else 1.0)
            },
        )
    },
)

/**
 * The track itself, fading from faint at the oldest end to solid at the newest.
 *
 * `line-gradient` needs the source to compute line-distance metrics, which is what `lineMetrics` turns on; without it
 * the gradient is silently ignored and the line draws in its base colour.
 */
@Composable
private fun TrackLineLayer(points: List<TrackPoint>, color: Color) {
    val source =
        rememberFeatureSource(
            FeatureCollection(
                listOf(
                    Feature<LineString, JsonObject?>(geometry = LineString(points.map { it.first }), properties = null),
                ),
            ),
            options = GeoJsonOptions(lineMetrics = true),
        )
    LineLayer(
        id = "track-line",
        source = source,
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
        width = const(3.dp),
        gradient =
        interpolate(
            linear(),
            feature.lineProgress(),
            0 to const(color.copy(alpha = OLDEST_ALPHA)),
            1 to const(color),
        ),
    )
}

/** Every recorded position, oldest faintest. Tapping one reports it so the caller's list can follow along. */
@Composable
private fun TrackPointLayer(points: List<TrackPoint>, color: Color, onPositionSelect: ((Int) -> Unit)?) {
    val source = rememberFeatureSource(pointFeatures(points))
    CircleLayer(
        id = "track-points",
        source = source,
        color = const(color),
        radius = const(5.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        // Faded rather than fully transparent at the oldest end. The Google flavor takes its alpha straight to zero,
        // which makes the first point of a track invisible and untappable.
        opacity = interpolate(linear(), feature[TRACK_AGE_KEY].asNumber(), 0 to const(OLDEST_ALPHA), 1 to const(1f)),
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

/** The node's own chip, at the head of the track. */
@Composable
private fun NewestPositionChip(node: Node, newest: TrackPoint) {
    val source =
        rememberFeatureSource(
            FeatureCollection(
                listOf(
                    Feature<Point, JsonObject?>(
                        geometry = Point(newest.first),
                        properties =
                        buildJsonObject {
                            put(NodeFeatureKeys.NODE_NUM, node.num)
                            put(NodeFeatureKeys.CHIP, node.toNodeChip().featureValue())
                        },
                    ),
                ),
            ),
        )
    NodeChipLayer(id = "track-head", source = source, nodes = listOf(node))
}

/** Alpha the oldest end of a track fades to. */
private const val OLDEST_ALPHA = 0.25f

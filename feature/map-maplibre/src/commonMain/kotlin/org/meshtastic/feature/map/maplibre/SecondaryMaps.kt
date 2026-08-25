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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position as GeoPosition
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.util.DiscoveryMapNode
import org.meshtastic.feature.map.maplibre.geojson.nodesToFeatureCollection
import org.meshtastic.feature.map.maplibre.layers.TracerouteLayers
import org.meshtastic.feature.map.maplibre.style.Basemaps
import org.meshtastic.proto.Position as ProtoPosition

private const val DEG_SCALE = 1e-7
private const val DETAIL_ZOOM = 13.0
private const val TRACK_POINT_KEY = "positionTime"

private val defaultStyle: BaseStyle
    get() = BaseStyle.Uri((Basemaps.default as org.meshtastic.feature.map.maplibre.style.Basemap.Vector).styleUri)

/** Single-node mini-map embedded in the node detail sheet. */
@Composable
fun MapLibreInlineMap(node: Node, modifier: Modifier = Modifier) {
    val position = node.validPosition
    val cameraState =
        rememberCameraState(
            CameraPosition(
                target = GeoPosition(longitude = node.longitude, latitude = node.latitude),
                zoom = DETAIL_ZOOM,
            ),
        )
    if (position == null) return

    MaplibreMap(baseStyle = defaultStyle, cameraState = cameraState, modifier = modifier) {
        val source = rememberGeoJsonSource(data = GeoJsonData.Features(nodesToFeatureCollection(listOf(node))))
        CircleLayer(
            id = "inline-node",
            source = source,
            color = const(Color(node.colors.second)),
            radius = const(10.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
        )
    }
}

/** Historic position track for one node, with tap-to-select synchronised to the caller's list. */
@Composable
fun MapLibreNodeTrackMap(
    destNum: Int,
    positions: List<ProtoPosition>,
    modifier: Modifier = Modifier,
    selectedPositionTime: Int? = null,
    onPositionSelected: ((Int) -> Unit)? = null,
) {
    val coordinates =
        positions.mapNotNull { p ->
            val lat = (p.latitude_i ?: 0) * DEG_SCALE
            val lon = (p.longitude_i ?: 0) * DEG_SCALE
            if (lat == 0.0 && lon == 0.0) null else GeoPosition(longitude = lon, latitude = lat) to (p.time ?: 0)
        }

    val cameraState = rememberCameraState()

    // Frame the whole track once it is known. Without this the camera opens at (0, 0), which is the
    // black-ocean start the OSMdroid track map suffered from.
    LaunchedEffect(coordinates.size) {
        if (coordinates.isNotEmpty()) {
            val lats = coordinates.map { it.first.latitude }
            val lons = coordinates.map { it.first.longitude }
            cameraState.jumpTo(
                CameraPosition(
                    target =
                    GeoPosition(
                        longitude = (lons.min() + lons.max()) / 2,
                        latitude = (lats.min() + lats.max()) / 2,
                    ),
                    zoom = DETAIL_ZOOM,
                ),
            )
        }
    }

    if (coordinates.isEmpty()) return

    MaplibreMap(baseStyle = defaultStyle, cameraState = cameraState, modifier = modifier) {
        val lineSource =
            rememberGeoJsonSource(
                data =
                GeoJsonData.Features(
                    FeatureCollection(
                        listOf(
                            Feature<LineString, JsonObject?>(
                                geometry = LineString(coordinates.map { it.first }),
                                properties = buildJsonObject { put("destNum", destNum) },
                            ),
                        ),
                    ),
                ),
            )
        LineLayer(
            id = "track-line",
            source = lineSource,
            cap = const(LineCap.Round),
            join = const(LineJoin.Round),
            color = const(Color(0xFF1E88E5)),
            width = const(3.dp),
        )

        val pointSource =
            rememberGeoJsonSource(
                data =
                GeoJsonData.Features(
                    FeatureCollection(
                        coordinates.map { (position, time) ->
                            Feature<Point, JsonObject?>(
                                geometry = Point(position),
                                properties = buildJsonObject { put(TRACK_POINT_KEY, time) },
                            )
                        },
                    ),
                ),
            )
        CircleLayer(
            id = "track-points",
            source = pointSource,
            color = const(Color.White),
            radius = const(5.dp),
            strokeColor = const(Color(0xFF1E88E5)),
            strokeWidth = const(2.dp),
            onClick = { features ->
                features.firstOrNull()?.properties?.get(TRACK_POINT_KEY)?.let { time ->
                    onPositionSelected?.invoke(time.jsonPrimitive.int)
                    ClickResult.Consume
                } ?: ClickResult.Pass
            },
        )

        selectedPositionTime?.let { selected ->
            val selectedSource =
                rememberGeoJsonSource(
                    data =
                    GeoJsonData.Features(
                        FeatureCollection(
                            coordinates
                                .filter { it.second == selected }
                                .map { (position, time) ->
                                    Feature<Point, JsonObject?>(
                                        geometry = Point(position),
                                        properties = buildJsonObject { put(TRACK_POINT_KEY, time) },
                                    )
                                },
                        ),
                    ),
                )
            CircleLayer(
                id = "track-selected",
                source = selectedSource,
                color = const(Color(0xFFFF8C00)),
                radius = const(8.dp),
                strokeColor = const(Color.White),
                strokeWidth = const(2.dp),
            )
        }
    }
}

/** Traceroute result map: forward and return paths plus the hops they pass through. */
@Composable
fun MapLibreTracerouteMap(
    forwardRoute: List<Int>,
    returnRoute: List<Int>,
    nodeLookup: Map<Int, Node>,
    modifier: Modifier = Modifier,
) {
    val cameraState = rememberCameraState()
    val hops = (forwardRoute + returnRoute).distinct().mapNotNull { nodeLookup[it] }

    LaunchedEffect(hops.size) { nodesBoundingBox(hops)?.let { cameraState.jumpTo(boundingBox = it) } }

    MaplibreMap(baseStyle = defaultStyle, cameraState = cameraState, modifier = modifier) {
        TracerouteLayers(forwardRoute = forwardRoute, returnRoute = returnRoute, nodeLookup = nodeLookup)

        val hopSource = rememberGeoJsonSource(data = GeoJsonData.Features(nodesToFeatureCollection(hops)))
        CircleLayer(
            id = "traceroute-hops",
            source = hopSource,
            color = const(Color(0xFF2C2D3C)),
            radius = const(10.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
        )
        SymbolLayer(
            id = "traceroute-hop-labels",
            source = hopSource,
            textField =
            feature[org.meshtastic.feature.map.maplibre.geojson.NodeFeatureKeys.SHORT_NAME].asString(),
            textColor = const(Color.White),
            textAllowOverlap = const(true),
        )
    }
}

/** Discovery scan summary map: the scanner plus everything it heard. */
@Composable
fun MapLibreDiscoveryMap(
    userLatitude: Double,
    userLongitude: Double,
    nodes: List<DiscoveryMapNode>,
    modifier: Modifier = Modifier,
) {
    val cameraState =
        rememberCameraState(
            CameraPosition(
                target = GeoPosition(longitude = userLongitude, latitude = userLatitude),
                zoom = DETAIL_ZOOM,
            ),
        )

    MaplibreMap(baseStyle = defaultStyle, cameraState = cameraState, modifier = modifier) {
        val discovered =
            rememberGeoJsonSource(
                data =
                GeoJsonData.Features(
                    FeatureCollection(
                        nodes.map { node ->
                            Feature<Point, JsonObject?>(
                                geometry =
                                Point(GeoPosition(longitude = node.longitude, latitude = node.latitude)),
                                properties =
                                buildJsonObject {
                                    put("shortName", node.shortName.orEmpty())
                                    put("snr", node.snr)
                                },
                            )
                        },
                    ),
                ),
            )
        CircleLayer(
            id = "discovery-nodes",
            source = discovered,
            color = const(Color(0xFF43A047)),
            radius = const(9.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
        )
        SymbolLayer(
            id = "discovery-labels",
            source = discovered,
            textField = feature["shortName"].asString(),
            textColor = const(Color.White),
            textAllowOverlap = const(true),
        )

        val scanner =
            rememberGeoJsonSource(
                data =
                GeoJsonData.Features(
                    FeatureCollection(
                        listOf(
                            Feature<Point, JsonObject?>(
                                geometry =
                                Point(GeoPosition(longitude = userLongitude, latitude = userLatitude)),
                                properties = buildJsonObject { put("role", "scanner") },
                            ),
                        ),
                    ),
                ),
            )
        CircleLayer(
            id = "discovery-scanner",
            source = scanner,
            color = const(Color(0xFFFF8C00)),
            radius = const(11.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(3.dp),
        )
    }
}

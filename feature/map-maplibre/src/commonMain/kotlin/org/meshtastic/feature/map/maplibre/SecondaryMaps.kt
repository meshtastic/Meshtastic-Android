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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.asBoolean
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.snr
import org.meshtastic.core.resources.unknown
import org.meshtastic.core.resources.you
import org.meshtastic.core.ui.util.DiscoveryMapNode
import org.meshtastic.core.ui.util.DiscoveryNeighborType
import org.meshtastic.feature.map.maplibre.component.MapZoom
import org.meshtastic.feature.map.maplibre.component.MapZoomCompact
import org.meshtastic.feature.map.maplibre.component.SecondaryMapControls
import org.meshtastic.feature.map.maplibre.component.rememberBasemapSelection
import org.meshtastic.feature.map.maplibre.geojson.MapChipGlyph
import org.meshtastic.feature.map.maplibre.geojson.MapChipKey
import org.meshtastic.feature.map.maplibre.geojson.NodeFeatureKeys
import org.meshtastic.feature.map.maplibre.geojson.featureValue
import org.meshtastic.feature.map.maplibre.geojson.nodesToFeatureCollection
import org.meshtastic.feature.map.maplibre.geojson.rememberFeatureSource
import org.meshtastic.feature.map.maplibre.layers.MapChipLayer
import org.meshtastic.feature.map.maplibre.layers.NodeChipLayer
import org.meshtastic.feature.map.maplibre.layers.NodePrecisionLayer
import org.meshtastic.feature.map.maplibre.layers.RasterBasemapLayer
import org.meshtastic.feature.map.maplibre.layers.TracerouteLayers
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.MapColors
import org.meshtastic.feature.map.maplibre.style.toBaseStyle
import org.meshtastic.feature.map.maplibre.style.zoomRange
import org.maplibre.spatialk.geojson.Position as GeoPosition

internal const val DEG_SCALE = 1e-7
internal const val DETAIL_ZOOM = 13.0

/** Tighter than [DETAIL_ZOOM]: the node-detail mini-map shows one node, and the Google flavor opens it at 15. */
private const val INLINE_ZOOM = 15.0

/**
 * Single-node mini-map embedded in the node detail sheet.
 *
 * Follows the basemap preference, so it matches the map the user was just looking at.
 */
@Composable
fun MapLibreInlineMap(
    node: Node,
    modifier: Modifier = Modifier,
    customBasemaps: @Composable () -> List<Basemap.Raster> = { emptyList() },
) {
    if (node.validPosition == null) return
    val basemaps = rememberBasemapSelection(customBasemaps())
    val target = GeoPosition(longitude = node.longitude, latitude = node.latitude)
    val cameraState = rememberCameraState(CameraPosition(target = target, zoom = INLINE_ZOOM))

    // Follows the node as fresh positions arrive, as the Google mini-map does. Guarded on the current target so the
    // first composition — which rememberCameraState has already centred — does not animate to where the camera is.
    LaunchedEffect(target) {
        if (cameraState.position.target != target) {
            cameraState.animateTo(cameraState.position.copy(target = target))
        }
    }

    // Buttons instead of gestures, which is the trade the Google mini-map makes and for a reason that applies here
    // too: this map sits inside the node detail sheet's vertically scrolling column, and a pan or zoom gesture on it
    // is a gesture the column also wants. Whichever wins, the other feels broken. The buttons are what make giving
    // the gestures up affordable — without them this map would have no way in at all.
    Box(modifier = modifier) {
        MaplibreMap(
            baseStyle = basemaps.current.toBaseStyle(),
            cameraState = cameraState,
            modifier = Modifier.fillMaxSize(),
            options = InlineMapOptions,
            zoomRange = basemaps.current.zoomRange(),
        ) {
            (basemaps.current as? Basemap.Raster)?.let { RasterBasemapLayer(it) }

            val source = rememberFeatureSource(node) { nodesToFeatureCollection(listOf(node)) }

            // The node-detail sheet is where a degraded position matters most — it is the screen a user opens to ask
            // how precisely this node is placed. The Google mini-map draws the circle; this one left it out.
            NodePrecisionLayer(id = "inline-precision", nodes = listOf(node))
            NodeChipLayer(id = "inline-node", source = source, nodes = listOf(node))
        }

        MapZoomCompact(cameraState = cameraState, basemap = basemaps.current)
    }
}

/**
 * Traceroute result map: forward and return paths plus the hops they pass through.
 *
 * The Google flavor reaches this through its main map composable, so it arrives with the toolbar and the basemap
 * already chosen. Here it is its own composable and has to ask for both. No filter: a traceroute is the route it is,
 * and there is nothing on it the main map's filters apply to.
 */
@Composable
fun MapLibreTracerouteMap(
    forwardRoute: List<Int>,
    returnRoute: List<Int>,
    nodeLookup: Map<Int, Node>,
    modifier: Modifier = Modifier,
    customBasemaps: @Composable () -> List<Basemap.Raster> = { emptyList() },
) {
    val cameraState = rememberCameraState()
    val basemaps = rememberBasemapSelection(customBasemaps())
    val hops = (forwardRoute + returnRoute).distinct().mapNotNull { nodeLookup[it] }

    // Wait for the map to report a viewport before fitting. Fitting a bounding box needs a viewport size, and on
    // first composition there is none yet — the fit silently landed on a default, which is why this map used to open
    // zoomed past the ends of the route it exists to show.
    val hasViewport = cameraState.viewport != null
    LaunchedEffect(hops, hasViewport) {
        if (hasViewport) {
            // Padded, as the Google flavor pads its own bounds fit: an edge-to-edge fit puts the outermost hops under
            // the toolbar and the legend.
            nodesBoundingBox(hops)?.let { cameraState.jumpTo(boundingBox = it, padding = SecondaryMapFitPadding) }
        }
    }

    Box(modifier = modifier) {
        MaplibreMap(
            baseStyle = basemaps.current.toBaseStyle(),
            cameraState = cameraState,
            modifier = Modifier.fillMaxSize(),
            options = SecondaryMapOptions,
            zoomRange = basemaps.current.zoomRange(),
        ) {
            (basemaps.current as? Basemap.Raster)?.let { RasterBasemapLayer(it) }
            TracerouteLayers(forwardRoute = forwardRoute, returnRoute = returnRoute, nodeLookup = nodeLookup)
            TracerouteHopLayers(hops)
        }
        MapZoom(cameraState = cameraState, basemap = basemaps.current)
        SecondaryMapControls(
            cameraState = cameraState,
            basemaps = basemaps,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = TOOLBAR_INSET.dp),
        )
    }
}

/** Every hop the route passes through, as the node chip it is elsewhere in the app. */
@Composable
private fun TracerouteHopLayers(hops: List<Node>) {
    val hopSource = rememberFeatureSource(hops) { nodesToFeatureCollection(hops) }
    NodeChipLayer(id = "traceroute-hops", source = hopSource, nodes = hops)
}

/**
 * Discovery scan summary map: the scanner plus everything it heard.
 *
 * Same treatment as the traceroute map, and for the same reason: satellite imagery is often the useful backdrop for
 * asking why one node was heard and another was not, and until now this map could not show it.
 */
@Composable
fun MapLibreDiscoveryMap(
    userLatitude: Double,
    userLongitude: Double,
    nodes: List<DiscoveryMapNode>,
    modifier: Modifier = Modifier,
    customBasemaps: @Composable () -> List<Basemap.Raster> = { emptyList() },
) {
    val scanner = GeoPosition(longitude = userLongitude, latitude = userLatitude)
    val cameraState = rememberCameraState(CameraPosition(target = scanner, zoom = DETAIL_ZOOM))
    val basemaps = rememberBasemapSelection(customBasemaps())
    // The node itself, not its index. The feature carries an index into `located`, which is only meaningful for
    // the composition that built it — a scan that reports another node afterwards renumbers the list, and a held
    // index would then describe somebody else. Resolving at tap time closes that window.
    var selectedNode by remember { mutableStateOf<DiscoveryMapNode?>(null) }

    // A discovered node can be heard without ever reporting a position, and drawing one at (0, 0) would put a chip in
    // the Gulf of Guinea and stretch the bounds fit across an ocean. DiscoveryMapScreen already drops those, as does
    // the Google discovery map — this does not depend on that, and gives every layer and the tap lookup below one
    // list to agree on.
    val located = remember(nodes) { nodes.filter { it.latitude != 0.0 || it.longitude != 0.0 } }

    // Frame the scanner together with everything it heard, which is the OSMdroid map's behaviour and the only view
    // that answers the question this map is opened to answer. Opening at a fixed zoom on the scanner put every
    // discovered node off screen. Waits for a viewport, since fitting a bounding box needs one.
    val hasViewport = cameraState.viewport != null
    LaunchedEffect(located, hasViewport) {
        if (hasViewport) {
            discoveryBoundingBox(scanner, located)?.let {
                cameraState.jumpTo(boundingBox = it, padding = SecondaryMapFitPadding)
            }
        }
    }

    Box(modifier = modifier) {
        MaplibreMap(
            baseStyle = basemaps.current.toBaseStyle(),
            cameraState = cameraState,
            modifier = Modifier.fillMaxSize(),
            options = SecondaryMapOptions,
            zoomRange = basemaps.current.zoomRange(),
        ) {
            (basemaps.current as? Basemap.Raster)?.let { RasterBasemapLayer(it) }
            DiscoveryTopologyLayer(scanner = scanner, nodes = located)
            DiscoveredNodeLayers(nodes = located, onNodeClick = { selectedNode = located.getOrNull(it) })
            ScannerLayer(scanner = scanner, label = stringResource(Res.string.you))
        }
        MapZoom(cameraState = cameraState, basemap = basemaps.current)
        SecondaryMapControls(
            cameraState = cameraState,
            basemaps = basemaps,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = TOOLBAR_INSET.dp),
        )

        // The signal figures the Google map shows in a marker snippet. A MapLibre marker is a layer feature and has no
        // snippet, so the tapped node's numbers go at the foot of the map.
        DiscoveryNodeCard(
            node = selectedNode,
            // Clear of the logo and attribution row, which the styles are licensed on condition of showing.
            modifier =
            Modifier.align(Alignment.BottomStart)
                .padding(start = CARD_INSET.dp, end = CARD_INSET.dp, bottom = ORNAMENT_CLEARANCE.dp),
        )
    }
}

/** Name and signal for the discovered node the user tapped. Draws nothing when nothing is selected. */
@Composable
private fun DiscoveryNodeCard(node: DiscoveryMapNode?, modifier: Modifier = Modifier) {
    if (node == null) return

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(CARD_PADDING.dp)) {
            Text(
                text = node.longName ?: node.shortName ?: stringResource(Res.string.unknown),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text =
                stringResource(Res.string.snr) +
                    ": " +
                    MetricFormatter.snr(node.snr) +
                    "   " +
                    stringResource(Res.string.rssi) +
                    ": " +
                    MetricFormatter.rssi(node.rssi),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * A line from the scanner to each located node it heard, coloured by how it was heard.
 *
 * The OSMdroid map draws these, and they carry the point of a discovery scan: markers alone say where nodes are, the
 * lines say which of them answered directly and which arrived through the mesh. Drawn before the markers so the lines
 * pass under them.
 */
@Composable
private fun DiscoveryTopologyLayer(scanner: GeoPosition, nodes: List<DiscoveryMapNode>) {
    val source =
        rememberFeatureSource(scanner, nodes) {
            FeatureCollection(
                nodes.map { node ->
                    Feature<LineString, JsonObject?>(
                        geometry =
                        LineString(
                            listOf(scanner, GeoPosition(longitude = node.longitude, latitude = node.latitude)),
                        ),
                        properties =
                        buildJsonObject { put(DIRECT_KEY, node.neighborType == DiscoveryNeighborType.DIRECT) },
                    )
                },
            )
        }

    LineLayer(
        id = "discovery-direct-links",
        source = source,
        filter = feature[DIRECT_KEY].asBoolean(),
        cap = const(LineCap.Round),
        color = const(MapColors.DiscoveryDirect),
        width = const(3.dp),
    )
    LineLayer(
        id = "discovery-mesh-links",
        source = source,
        filter = !feature[DIRECT_KEY].asBoolean(),
        cap = const(LineCap.Round),
        color = const(MapColors.DiscoveryMesh),
        width = const(2.dp),
        opacity = const(MESH_LINK_OPACITY),
    )
}

/**
 * Every node the scan heard, as a chip in the colour of how it was heard.
 *
 * Green for direct, blue through the mesh — the Google discovery map's own pairing. Tapping one reports it so the
 * caller can show the signal figures, which is what that map puts in a marker snippet.
 */
@Composable
private fun DiscoveredNodeLayers(nodes: List<DiscoveryMapNode>, onNodeClick: (Int) -> Unit) {
    val chips = remember(nodes) { nodes.map { it.toMapChip() } }
    val source =
        rememberFeatureSource(nodes) {
            FeatureCollection(
                nodes.mapIndexed { index, node ->
                    Feature<Point, JsonObject?>(
                        geometry = Point(GeoPosition(longitude = node.longitude, latitude = node.latitude)),
                        properties =
                        buildJsonObject {
                            put(DISCOVERY_INDEX_KEY, index)
                            put(NodeFeatureKeys.CHIP, chips[index].featureValue())
                        },
                    )
                },
            )
        }
    MapChipLayer(
        id = "discovery-nodes",
        source = source,
        chips = chips,
        onClick = { features ->
            features.firstOrNull()?.properties?.get(DISCOVERY_INDEX_KEY)?.let { index ->
                onNodeClick(index.jsonPrimitive.int)
                ClickResult.Consume
            } ?: ClickResult.Pass
        },
    )
}

/**
 * Green when the scanner heard this node itself, blue when it arrived through the mesh.
 *
 * A node whose traffic is mostly environment telemetry shows a thermometer and one that mostly talks shows a person,
 * instead of its short name — the same substitution the Google discovery map makes.
 */
private fun DiscoveryMapNode.toMapChip() = MapChipKey(
    label = shortName?.takeIf { it.isNotBlank() } ?: UNKNOWN_CHIP_LABEL,
    background =
    if (neighborType == DiscoveryNeighborType.DIRECT) {
        MapColors.DiscoveryDirect.toArgb()
    } else {
        MapColors.DiscoveryMesh.toArgb()
    },
    foreground = Color.White.toArgb(),
    outlined = true,
    glyph = if (isSensorNode) MapChipGlyph.SENSOR else MapChipGlyph.SOCIAL,
)

/** The scanner's own position, labelled — the Google discovery map puts a "You" chip here. */
@Composable
private fun ScannerLayer(scanner: GeoPosition, label: String) {
    val chip =
        MapChipKey(
            label = label,
            background = MapColors.DiscoveryUser.toArgb(),
            foreground = Color.White.toArgb(),
            outlined = true,
        )
    val source =
        rememberFeatureSource(scanner, chip) {
            FeatureCollection(
                listOf(
                    Feature<Point, JsonObject?>(
                        geometry = Point(scanner),
                        properties = buildJsonObject { put(NodeFeatureKeys.CHIP, chip.featureValue()) },
                    ),
                ),
            )
        }
    MapChipLayer(id = "discovery-scanner", source = source, chips = listOf(chip))
}

/**
 * Gesture set for the small informational maps.
 *
 * Tilt is off. These are compact, often only a couple of hundred pixels tall, and they carry no toolbar — so a stray
 * two-finger drag pitches the map with nothing on screen to put it back. The Google flavor makes the same call with
 * `tiltGesturesEnabled = isMainMode`, and the OSMdroid map never had tilt at all. Rotation stays, which is what Google
 * does too.
 */
internal val SecondaryMapOptions =
    MapOptions(gestureOptions = GestureOptions(isDragRotateTiltEnabled = false, isTwoFingerTiltEnabled = false))

/**
 * Gestures off entirely, for the mini-map embedded in the node detail sheet.
 *
 * That sheet scrolls vertically, and every gesture this map would claim — a drag to pan, a pinch or wheel to zoom — is
 * one the column is also trying to read. The Google flavor resolves it the same way, turning off scroll, zoom, rotate
 * and tilt in its own `InlineMap` and leaving the zoom buttons as the only way in. Keyboard pan and zoom stay: they
 * cost the column nothing and are the desktop's route to the same thing.
 */
internal val InlineMapOptions =
    MapOptions(
        gestureOptions =
        GestureOptions(
            isDragPanEnabled = false,
            isDragRotateTiltEnabled = false,
            isPinchZoomEnabled = false,
            isTwoFingerRotateEnabled = false,
            isTwoFingerTiltEnabled = false,
            isTwoFingerTapZoomEnabled = false,
            isScrollZoomEnabled = false,
            isDoubleClickZoomEnabled = false,
            isQuickZoomEnabled = false,
            isFlingEnabled = false,
            isPinchZoomVelocityEnabled = false,
            isRotateVelocityEnabled = false,
        ),
    )

/** Keeps a floating toolbar clear of the top edge, matching the main map's own inset. */
internal const val TOOLBAR_INSET = 8

/**
 * Breathing room around a bounds fit, per edge, so the outermost points do not land under the chrome.
 *
 * Asymmetric because the chrome is: the toolbar floats at the top, the zoom pair and the attribution button share the
 * lower trailing corner, and a node chip extends half its own width past the point it marks. An even inset wide enough
 * for the worst edge would waste the other three.
 */
internal val SecondaryMapFitPadding = PaddingValues(start = 48.dp, top = 64.dp, end = 88.dp, bottom = 56.dp)

/** Inset for a detail card at the foot of a map. */
internal const val CARD_INSET = 8

/**
 * Height to leave for the map's logo and attribution row, which the styles are licensed on condition of showing.
 *
 * See [org.meshtastic.feature.map.maplibre.component.MeshMapOrnaments].
 */
internal const val ORNAMENT_CLEARANCE = 40
private const val CARD_PADDING = 8

/** A box covering the scanner and every located node it heard. Callers pass an already-filtered list. */
private fun discoveryBoundingBox(scanner: GeoPosition, nodes: List<DiscoveryMapNode>): BoundingBox? {
    if (nodes.isEmpty()) return null

    return positionsBoundingBox(
        nodes.map { GeoPosition(longitude = it.longitude, latitude = it.latitude) } + listOf(scanner),
    )
}

/** Whether the scanner heard this node itself, rather than through the mesh. */
private const val DIRECT_KEY = "direct"

/** Index into the discovery node list, so a tap can name which node was hit. */
private const val DISCOVERY_INDEX_KEY = "discoveryIndex"
private const val UNKNOWN_CHIP_LABEL = "?"
private const val MESH_LINK_OPACITY = 0.7f

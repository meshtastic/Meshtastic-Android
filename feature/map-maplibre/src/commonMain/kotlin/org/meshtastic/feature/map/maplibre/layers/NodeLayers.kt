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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.maplibre.geojson.NodeFeatureKeys
import org.meshtastic.feature.map.maplibre.geojson.nodesToFeatureCollection
import org.meshtastic.feature.map.maplibre.geojson.precisionCirclesToFeatureCollection
import org.meshtastic.feature.map.maplibre.style.MapColors

private const val CLUSTER_RADIUS = 50
private const val CLUSTER_MAX_ZOOM = 14

/**
 * The mesh node layers: ground-truth precision circles underneath, then clusters, then individual node chips.
 *
 * Clustering is MapLibre's own — `point_count` is computed inside the source — which is why the OSMdroid clustering
 * code has no counterpart here.
 *
 * @param onClusterZoom invoked with the cluster centre and the zoom that breaks it apart; the caller owns the camera.
 *   MapLibre reports a sentinel when a cluster cannot compute an expansion zoom, so the caller must clamp against the
 *   current zoom rather than trusting the value.
 */
@Composable
@Suppress("LongMethod")
internal fun NodeLayers(
    nodes: List<Node>,
    myNodeNum: Int?,
    showPrecisionCircles: Boolean,
    onNodeClick: (Int) -> Unit,
    onClusterZoom: (Position, Double) -> Unit,
) {
    if (showPrecisionCircles) {
        val precisionSource =
            rememberGeoJsonSource(data = GeoJsonData.Features(precisionCirclesToFeatureCollection(nodes)))
        FillLayer(
            id = "node-precision-fill",
            source = precisionSource,
            color = feature[NodeFeatureKeys.BACKGROUND].convertToColor(const(Color.Gray)),
            opacity = const(PRECISION_FILL_OPACITY),
            outlineColor = feature[NodeFeatureKeys.BACKGROUND].convertToColor(const(Color.Gray)),
        )
    }

    // getClusterExpansionZoom became a suspend function in maplibre-compose 0.15.0 (feature queries
    // no longer block the caller), and onClick is not a suspending callback.
    val clusterScope = rememberCoroutineScope()

    val nodeSource =
        rememberGeoJsonSource(
            data = GeoJsonData.Features(nodesToFeatureCollection(nodes, myNodeNum)),
            options = GeoJsonOptions(cluster = true, clusterRadius = CLUSTER_RADIUS, clusterMaxZoom = CLUSTER_MAX_ZOOM),
        )

    CircleLayer(
        id = "node-clusters",
        source = nodeSource,
        filter = feature.has("point_count"),
        color = const(MapColors.Slate),
        opacity = const(CLUSTER_OPACITY),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        radius =
        step(
            input = feature["point_count"].asNumber(),
            fallback = const(16.dp),
            CLUSTER_STEP_SMALL to const(20.dp),
            CLUSTER_STEP_MEDIUM to const(26.dp),
            CLUSTER_STEP_LARGE to const(32.dp),
        ),
        onClick = { features ->
            features
                .firstOrNull { nodeSource.isCluster(it) }
                ?.let { clusterFeature ->
                    val centre = (clusterFeature.geometry as? Point)?.coordinates
                    if (centre != null) {
                        clusterScope.launch {
                            onClusterZoom(centre, nodeSource.getClusterExpansionZoom(clusterFeature))
                        }
                    }
                    ClickResult.Consume
                } ?: ClickResult.Pass
        },
    )

    SymbolLayer(
        id = "node-cluster-count",
        source = nodeSource,
        filter = feature.has("point_count"),
        textField = feature["point_count_abbreviated"].asString(),
        textColor = const(Color.White),
        textFont = const(listOf("Noto Sans Regular")),
    )

    CircleLayer(
        id = "node-chip",
        source = nodeSource,
        filter = !feature.has("point_count"),
        color = feature[NodeFeatureKeys.BACKGROUND].convertToColor(const(Color.Gray)),
        radius = const(14.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        onClick = { features ->
            features.firstOrNull()?.properties?.get(NodeFeatureKeys.NODE_NUM)?.let { nodeNum ->
                onNodeClick(nodeNum.jsonPrimitive.int)
                ClickResult.Consume
            } ?: ClickResult.Pass
        },
    )

    SymbolLayer(
        id = "node-chip-label",
        source = nodeSource,
        filter = !feature.has("point_count"),
        textField = feature[NodeFeatureKeys.SHORT_NAME].asString(),
        textFont = const(listOf("Noto Sans Regular")),
        textColor = feature[NodeFeatureKeys.FOREGROUND].convertToColor(const(MaterialTheme.colorScheme.onSurface)),
        textAllowOverlap = const(true),
    )
}

private const val PRECISION_FILL_OPACITY = 0.15f
private const val CLUSTER_OPACITY = 0.9f
private const val CLUSTER_STEP_SMALL = 10
private const val CLUSTER_STEP_MEDIUM = 50
private const val CLUSTER_STEP_LARGE = 200

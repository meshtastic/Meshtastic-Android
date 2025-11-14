/*
 * Copyright (c) 2025 Meshtastic LLC
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

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.MapLibre
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.meshtastic.core.database.model.Node
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.MapViewModel
import org.meshtastic.proto.MeshProtos.Waypoint

private const val DEG_D = 1e-7

private const val STYLE_URL = "https://demotiles.maplibre.org/style.json"

private const val NODES_SOURCE_ID = "meshtastic-nodes-source"
private const val WAYPOINTS_SOURCE_ID = "meshtastic-waypoints-source"
private const val TRACKS_SOURCE_ID = "meshtastic-tracks-source"

private const val NODES_LAYER_ID = "meshtastic-nodes-layer"
private const val WAYPOINTS_LAYER_ID = "meshtastic-waypoints-layer"
private const val TRACKS_LAYER_ID = "meshtastic-tracks-layer"

@SuppressLint("MissingPermission")
@Composable
fun MapLibrePOC(
    mapViewModel: MapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val nodes by mapViewModel.nodes.collectAsStateWithLifecycle()
    val waypoints by mapViewModel.waypoints.collectAsStateWithLifecycle()

    AndroidView<MapView>(
        modifier = Modifier.fillMaxSize(),
        factory = {
            MapLibre.getInstance(context)
            MapView(context).apply {
                getMapAsync { map ->
                    map.setStyle(STYLE_URL) { style ->
                        ensureSourcesAndLayers(style)
                        // Enable location component (if permissions granted)
                        try {
                            val locationComponent = map.locationComponent
                            locationComponent.activateLocationComponent(
                                org.maplibre.android.location.LocationComponentActivationOptions.builder(
                                    context,
                                    style,
                                ).useDefaultLocationEngine(true).build(),
                            )
                            locationComponent.isLocationComponentEnabled = true
                            locationComponent.renderMode = RenderMode.COMPASS
                        } catch (_: Throwable) {
                            // Location component may fail if permissions are not granted; ignore for POC
                        }
                    }
                }
            }
        },
        update = { mapView: MapView ->
            mapView.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync
                // Update nodes
                (style.getSource(NODES_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(nodesToFeatureCollectionJson(nodes))
                // Update waypoints
                (style.getSource(WAYPOINTS_SOURCE_ID) as? GeoJsonSource)
                    ?.setGeoJson(waypointsToFeatureCollectionJson(waypoints.values))
                // Tracks are optional in POC: keep empty for now
                (style.getSource(TRACKS_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(emptyFeatureCollectionJson())
            }
        },
    )

    // Forward lifecycle events to MapView
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                // Note: AndroidView handles View lifecycle, but MapView benefits from explicit forwarding
                when (event) {
                    Lifecycle.Event.ON_START -> {}
                    Lifecycle.Event.ON_RESUME -> {}
                    Lifecycle.Event.ON_PAUSE -> {}
                    Lifecycle.Event.ON_STOP -> {}
                    Lifecycle.Event.ON_DESTROY -> {}
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

}

private fun ensureSourcesAndLayers(style: Style) {
    if (style.getSource(NODES_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(NODES_SOURCE_ID, emptyFeatureCollectionJson()))
    }
    if (style.getSource(WAYPOINTS_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(WAYPOINTS_SOURCE_ID, emptyFeatureCollectionJson()))
    }
    if (style.getSource(TRACKS_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(TRACKS_SOURCE_ID, emptyFeatureCollectionJson()))
    }

    if (style.getLayer(NODES_LAYER_ID) == null) {
        // Use circles for POC to avoid image dependencies
        val layer = SymbolLayer(NODES_LAYER_ID, NODES_SOURCE_ID)
        // A minimal circle-like via icon is more complex; switch to CircleLayer later if desired
        // Using SymbolLayer with default icon is okay for POC; alternatively, CircleLayer:
        // val layer = CircleLayer(NODES_LAYER_ID, NODES_SOURCE_ID).withProperties(circleColor("#1E88E5"), circleRadius(6f))
        style.addLayer(layer)
    }
    if (style.getLayer(WAYPOINTS_LAYER_ID) == null) {
        val layer = SymbolLayer(WAYPOINTS_LAYER_ID, WAYPOINTS_SOURCE_ID)
        style.addLayer(layer)
    }
    if (style.getLayer(TRACKS_LAYER_ID) == null) {
        val layer = LineLayer(TRACKS_LAYER_ID, TRACKS_SOURCE_ID).withProperties(lineColor("#FF6D00"), lineWidth(3f))
        style.addLayer(layer)
    }
}

private fun nodesToFeatureCollectionJson(nodes: List<Node>): String {
    val features =
        nodes.mapNotNull { node ->
            val pos = node.position ?: return@mapNotNull null
            val lat = pos.latitudeI * DEG_D
            val lon = pos.longitudeI * DEG_D
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{}}"""
        }
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

private fun waypointsToFeatureCollectionJson(
    waypoints: Collection<org.meshtastic.core.database.entity.Packet>,
): String {
    val features =
        waypoints.mapNotNull { pkt ->
            val w: Waypoint = pkt.data.waypoint ?: return@mapNotNull null
            val lat = w.latitudeI * DEG_D
            val lon = w.longitudeI * DEG_D
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{}}"""
        }
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

private fun emptyFeatureCollectionJson(): String {
    return """{"type":"FeatureCollection","features":[]}"""
}



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
@file:Suppress("MagicNumber")

package org.meshtastic.app.map.discovery

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.meshtastic.app.map.addCopyright
import org.meshtastic.app.map.addScaleBarOverlay
import org.meshtastic.app.map.model.CustomTileSource
import org.meshtastic.app.map.rememberMapViewWithLifecycle
import org.meshtastic.app.map.zoomIn
import org.meshtastic.core.ui.theme.DiscoveryMapColors
import org.meshtastic.core.ui.util.DiscoveryMapNode
import org.meshtastic.core.ui.util.DiscoveryNeighborType
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private const val SINGLE_POINT_ZOOM = 14.0
private const val ZOOM_OUT_LEVELS = 0.5

/**
 * OSMDroid implementation of the discovery map. Renders discovered node markers color-coded by neighbor type (green =
 * direct, blue = mesh) with polylines from the user position to direct neighbors. Auto-zooms to fit all markers.
 */
@Composable
fun DiscoveryOsmMap(
    userLatitude: Double,
    userLongitude: Double,
    nodes: List<DiscoveryMapNode>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val hasValidUserPosition = userLatitude != 0.0 || userLongitude != 0.0
    val userGeoPoint = remember(userLatitude, userLongitude) { GeoPoint(userLatitude, userLongitude) }
    val validNodes = remember(nodes) { nodes.filter { it.latitude != 0.0 || it.longitude != 0.0 } }

    // Build bounding box from all points
    val allGeoPoints =
        remember(validNodes, hasValidUserPosition) {
            buildList {
                if (hasValidUserPosition) add(userGeoPoint)
                validNodes.forEach { add(GeoPoint(it.latitude, it.longitude)) }
            }
        }
    val initialBounds =
        remember(allGeoPoints) {
            if (allGeoPoints.isEmpty()) BoundingBox() else BoundingBox.fromGeoPoints(allGeoPoints)
        }

    var hasCentered by remember { mutableStateOf(false) }

    val mapView =
        rememberMapViewWithLifecycle(
            applicationId = context.packageName,
            box = initialBounds,
            tileSource = CustomTileSource.getTileSource(0),
        )

    // Camera auto-center once
    LaunchedEffect(allGeoPoints) {
        if (hasCentered || allGeoPoints.isEmpty()) return@LaunchedEffect
        if (allGeoPoints.size == 1) {
            mapView.controller.setCenter(allGeoPoints.first())
            mapView.controller.setZoom(SINGLE_POINT_ZOOM)
        } else {
            mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(allGeoPoints).zoomIn(-ZOOM_OUT_LEVELS), true)
        }
        hasCentered = true
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView.apply { setDestroyMode(false) } },
        update = { map ->
            map.overlays.clear()
            map.addCopyright()
            map.addScaleBarOverlay(density)

            // User position marker
            if (hasValidUserPosition) {
                val userMarker =
                    Marker(map).apply {
                        position = userGeoPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Your Position"
                        icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)
                    }
                map.overlays.add(userMarker)
            }

            // Node markers
            validNodes.forEach { node ->
                val nodeGeoPoint = GeoPoint(node.latitude, node.longitude)
                val marker =
                    Marker(map).apply {
                        position = nodeGeoPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = node.longName ?: node.shortName ?: "Unknown"
                        snippet = "SNR: ${node.snr} dB / RSSI: ${node.rssi} dBm"

                        val drawableId =
                            if (node.isSensorNode) {
                                org.meshtastic.app.R.drawable.ic_thermostat
                            } else {
                                org.meshtastic.app.R.drawable.ic_person
                            }
                        icon = context.getDrawable(drawableId)

                        // Default OSM marker handles color tinting via icon overlay or custom drawables if needed,
                        // but setting the icon directly overrides the default teardrop pin.
                    }
                map.overlays.add(marker)
            }

            // Polylines from user to direct neighbors
            if (hasValidUserPosition) {
                validNodes
                    .filter { it.neighborType == DiscoveryNeighborType.DIRECT }
                    .forEach { node ->
                        val polyline =
                            Polyline().apply {
                                setPoints(listOf(userGeoPoint, GeoPoint(node.latitude, node.longitude)))
                                outlinePaint.apply {
                                    color = DiscoveryMapColors.DirectLine.toArgb()
                                    strokeWidth = with(density) { 3.dp.toPx() }
                                    strokeCap = Paint.Cap.ROUND
                                    style = Paint.Style.STROKE
                                }
                            }
                        map.overlays.add(polyline)
                    }
            }

            map.invalidate()
        },
    )
}

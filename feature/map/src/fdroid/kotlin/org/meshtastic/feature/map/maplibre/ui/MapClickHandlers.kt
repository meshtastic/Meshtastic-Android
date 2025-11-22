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

package org.meshtastic.feature.map.maplibre.ui

import android.content.Context
import android.graphics.PointF
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import org.meshtastic.data.entities.Node
import org.meshtastic.data.entities.Packet
import org.meshtastic.feature.map.maplibre.MapLibreConstants.CLUSTER_CIRCLE_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_CLUSTER_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_LAYER_NOCLUSTER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.WAYPOINTS_LAYER_ID
import org.meshtastic.proto.MeshProtos.Waypoint
import timber.log.Timber

data class ExpandedCluster(val centerPx: PointF, val members: List<Node>)

// Constants
private const val HIT_BOX_RADIUS_DP = 24f
private const val CLUSTER_LIST_FETCH_MAX = 200
private const val CLUSTER_RADIAL_MAX = 10

/**
 * Creates a map click listener that handles cluster expansion, node selection, and waypoint editing.
 *
 * @param map The MapLibre map instance
 * @param context Android context for density calculations
 * @param nodes All available nodes
 * @param waypoints All available waypoints
 * @param onExpandedClusterChange Callback when cluster overlay should be shown/hidden
 * @param onClusterListMembersChange Callback when cluster list bottom sheet should be shown/hidden
 * @param onSelectedNodeChange Callback when a node is selected
 * @param onWaypointEditRequest Callback when a waypoint should be edited
 */
fun createMapClickListener(
    map: MapLibreMap,
    context: Context,
    nodes: List<Node>,
    waypoints: Map<String, Packet>,
    onExpandedClusterChange: (ExpandedCluster?) -> Unit,
    onClusterListMembersChange: (List<Node>?) -> Unit,
    onSelectedNodeChange: (Int?) -> Unit,
    onWaypointEditRequest: (Waypoint?) -> Unit,
): MapLibreMap.OnMapClickListener {
    return MapLibreMap.OnMapClickListener { latLng ->
        // Any tap on the map clears overlays unless replaced below
        onExpandedClusterChange(null)
        onClusterListMembersChange(null)

        val screenPoint = map.projection.toScreenLocation(latLng)
        // Use a small hitbox to improve taps on small circles
        val r = (HIT_BOX_RADIUS_DP * context.resources.displayMetrics.density)
        val rect = android.graphics.RectF(
            (screenPoint.x - r).toFloat(),
            (screenPoint.y - r).toFloat(),
            (screenPoint.x + r).toFloat(),
            (screenPoint.y + r).toFloat(),
        )

        val features = map.queryRenderedFeatures(
            rect,
            CLUSTER_CIRCLE_LAYER_ID,
            NODES_LAYER_ID,
            NODES_LAYER_NOCLUSTER_ID,
            WAYPOINTS_LAYER_ID,
        )

        Timber.tag("MapClickHandlers").d(
            "Map click at (%.5f, %.5f) -> %d features",
            latLng.latitude,
            latLng.longitude,
            features.size,
        )

        val f = features.firstOrNull()

        // If cluster tapped, expand using true cluster leaves from the source
        if (f != null && f.hasProperty("point_count")) {
            val pointCount = f.getNumberProperty("point_count")?.toInt() ?: 0
            val limit = kotlin.math.min(CLUSTER_LIST_FETCH_MAX, pointCount.toLong())
            val src = (map.style?.getSource(NODES_CLUSTER_SOURCE_ID) as? GeoJsonSource)

            if (src != null) {
                val fc = src.getClusterLeaves(f, limit, 0L)
                val nums = fc.features()?.mapNotNull { feat ->
                    try {
                        feat.getNumberProperty("num")?.toInt()
                    } catch (_: Throwable) {
                        null
                    }
                } ?: emptyList()

                val members = nodes.filter { nums.contains(it.num) }

                if (members.isNotEmpty()) {
                    // Center camera on cluster (without zoom) to keep cluster intact
                    val geom = f.geometry()
                    if (geom is Point) {
                        val clusterLat = geom.latitude()
                        val clusterLon = geom.longitude()
                        val clusterLatLng = LatLng(clusterLat, clusterLon)

                        map.animateCamera(
                            CameraUpdateFactory.newLatLng(clusterLatLng),
                            300,
                            object : MapLibreMap.CancelableCallback {
                                override fun onFinish() {
                                    // Calculate screen position AFTER camera animation completes
                                    val clusterCenter = map.projection.toScreenLocation(
                                        LatLng(clusterLat, clusterLon),
                                    )

                                    // Set overlay state after camera animation completes
                                    if (pointCount > CLUSTER_RADIAL_MAX) {
                                        // Show list for large clusters
                                        onClusterListMembersChange(members)
                                    } else {
                                        // Show radial overlay for small clusters
                                        onExpandedClusterChange(
                                            ExpandedCluster(clusterCenter, members.take(CLUSTER_RADIAL_MAX))
                                        )
                                    }
                                }

                                override fun onCancel() {
                                    // Animation was cancelled, don't show overlay
                                }
                            },
                        )

                        Timber.tag("MapClickHandlers").d(
                            "Centering on cluster at (%.5f, %.5f) with %d members",
                            clusterLat,
                            clusterLon,
                            members.size,
                        )
                    } else {
                        // No geometry, show overlay immediately using current screen position
                        val clusterCenter = (f.geometry() as? Point)?.let { p ->
                            map.projection.toScreenLocation(LatLng(p.latitude(), p.longitude()))
                        } ?: screenPoint

                        if (pointCount > CLUSTER_RADIAL_MAX) {
                            onClusterListMembersChange(members)
                        } else {
                            onExpandedClusterChange(
                                ExpandedCluster(clusterCenter, members.take(CLUSTER_RADIAL_MAX))
                            )
                        }
                    }
                }
                return@OnMapClickListener true
            } else {
                map.animateCamera(CameraUpdateFactory.zoomIn())
                return@OnMapClickListener true
            }
        }

        // Handle node/waypoint selection
        f?.let {
            val kind = it.getStringProperty("kind")
            when (kind) {
                "node" -> {
                    val num = it.getNumberProperty("num")?.toInt() ?: -1
                    onSelectedNodeChange(num)

                    // Center camera on selected node
                    val geom = it.geometry()
                    if (geom is Point) {
                        val nodeLat = geom.latitude()
                        val nodeLon = geom.longitude()
                        val nodeLatLng = LatLng(nodeLat, nodeLon)
                        map.animateCamera(CameraUpdateFactory.newLatLng(nodeLatLng), 300)
                        Timber.tag("MapClickHandlers").d(
                            "Centering on node %d at (%.5f, %.5f)",
                            num,
                            nodeLat,
                            nodeLon,
                        )
                    }
                }
                "waypoint" -> {
                    val id = it.getNumberProperty("id")?.toInt() ?: -1
                    // Open edit dialog for waypoint
                    val waypoint = waypoints.values
                        .find { pkt -> pkt.data.waypoint?.id == id }
                        ?.data?.waypoint
                    onWaypointEditRequest(waypoint)

                    // Center camera on waypoint
                    val geom = it.geometry()
                    if (geom is Point) {
                        val wpLat = geom.latitude()
                        val wpLon = geom.longitude()
                        val wpLatLng = LatLng(wpLat, wpLon)
                        map.animateCamera(CameraUpdateFactory.newLatLng(wpLatLng), 300)
                        Timber.tag("MapClickHandlers").d(
                            "Centering on waypoint %d at (%.5f, %.5f)",
                            id,
                            wpLat,
                            wpLon,
                        )
                    }
                }
                else -> {}
            }
        }
        true
    }
}

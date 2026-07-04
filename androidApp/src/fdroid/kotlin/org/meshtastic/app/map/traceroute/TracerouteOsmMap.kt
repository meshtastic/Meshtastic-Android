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

package org.meshtastic.app.map.traceroute

import android.graphics.Paint
import androidx.appcompat.content.res.AppCompatResources
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.R
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.app.map.addCopyright
import org.meshtastic.app.map.addScaleBarOverlay
import org.meshtastic.app.map.model.CustomTileSource
import org.meshtastic.app.map.model.MarkerWithLabel
import org.meshtastic.app.map.rememberMapViewWithLifecycle
import org.meshtastic.app.map.zoomIn
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.core.model.util.GeoConstants.EARTH_RADIUS_METERS
import org.meshtastic.core.ui.theme.TracerouteColors
import org.meshtastic.core.ui.util.formatAgo
import org.meshtastic.feature.map.tracerouteNodeSelection
import org.meshtastic.proto.Position
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val TRACEROUTE_OFFSET_METERS = 100.0
private const val TRACEROUTE_SINGLE_POINT_ZOOM = 12.0
private const val TRACEROUTE_ZOOM_OUT_LEVELS = 0.5

/**
 * A focused OSMDroid map composable that renders **only** traceroute visualization — node markers for each hop and
 * forward/return offset polylines with auto-centering camera.
 *
 * Unlike the main `MapView`, this composable does **not** include node clusters, waypoints, location tracking, or any
 * map controls. It is designed to be embedded inside `TracerouteMapScreen`'s scaffold.
 */
@Composable
fun TracerouteOsmMap(
    tracerouteOverlay: TracerouteOverlay?,
    tracerouteNodePositions: Map<Int, Position>,
    onMappableCountChanged: (shown: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val nodes by mapViewModel.nodes.collectAsStateWithLifecycle()
    val markerIcon = remember { AppCompatResources.getDrawable(context, R.drawable.ic_location_on) }

    // Resolve which nodes to display for the traceroute
    val tracerouteSelection =
        remember(tracerouteOverlay, tracerouteNodePositions, nodes) {
            mapViewModel.tracerouteNodeSelection(
                tracerouteOverlay = tracerouteOverlay,
                tracerouteNodePositions = tracerouteNodePositions,
                nodes = nodes,
            )
        }
    val displayNodes = tracerouteSelection.nodesForMarkers
    val nodeLookup = tracerouteSelection.nodeLookup

    // Report mappable count
    LaunchedEffect(tracerouteOverlay, displayNodes) {
        if (tracerouteOverlay != null) {
            onMappableCountChanged(displayNodes.size, tracerouteOverlay.relatedNodeNums.size)
        }
    }

    // Compute polyline GeoPoints from node positions
    val forwardPoints =
        remember(tracerouteOverlay, nodeLookup) {
            tracerouteOverlay?.forwardRoute?.mapNotNull {
                nodeLookup[it]?.let { node -> GeoPoint(node.latitude, node.longitude) }
            } ?: emptyList()
        }
    val returnPoints =
        remember(tracerouteOverlay, nodeLookup) {
            tracerouteOverlay?.returnRoute?.mapNotNull {
                nodeLookup[it]?.let { node -> GeoPoint(node.latitude, node.longitude) }
            } ?: emptyList()
        }

    // Compute offset polylines for visual separation
    val headingReferencePoints =
        remember(forwardPoints, returnPoints) {
            when {
                forwardPoints.size >= 2 -> forwardPoints
                returnPoints.size >= 2 -> returnPoints
                else -> emptyList()
            }
        }
    val forwardOffsetPoints =
        remember(forwardPoints, headingReferencePoints) {
            offsetPolyline(
                points = forwardPoints,
                offsetMeters = TRACEROUTE_OFFSET_METERS,
                headingReferencePoints = headingReferencePoints,
                sideMultiplier = 1.0,
            )
        }
    val returnOffsetPoints =
        remember(returnPoints, headingReferencePoints) {
            offsetPolyline(
                points = returnPoints,
                offsetMeters = TRACEROUTE_OFFSET_METERS,
                headingReferencePoints = headingReferencePoints,
                sideMultiplier = -1.0,
            )
        }

    // Camera auto-center
    var hasCentered by remember(tracerouteOverlay) { mutableStateOf(false) }

    // Build initial camera from all traceroute points
    val allPoints = remember(forwardPoints, returnPoints) { (forwardPoints + returnPoints).distinct() }
    val initialCameraView =
        remember(allPoints) { if (allPoints.isEmpty()) null else BoundingBox.fromGeoPoints(allPoints) }

    val mapView =
        rememberMapViewWithLifecycle(
            applicationId = mapViewModel.applicationId,
            box = initialCameraView ?: BoundingBox(),
            tileSource = CustomTileSource.getTileSource(mapViewModel.mapStyleId),
        )

    // Center camera on traceroute bounds
    LaunchedEffect(tracerouteOverlay, forwardPoints, returnPoints) {
        if (tracerouteOverlay == null || hasCentered) return@LaunchedEffect
        if (allPoints.isNotEmpty()) {
            if (allPoints.size == 1) {
                mapView.controller.setCenter(allPoints.first())
                mapView.controller.setZoom(TRACEROUTE_SINGLE_POINT_ZOOM)
            } else {
                mapView.zoomToBoundingBox(
                    BoundingBox.fromGeoPoints(allPoints).zoomIn(-TRACEROUTE_ZOOM_OUT_LEVELS),
                    true,
                )
            }
            hasCentered = true
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView.apply { setDestroyMode(false) } },
        update = { map ->
            map.overlays.clear()
            map.addCopyright()
            map.addScaleBarOverlay(density)

            // Render traceroute polylines
            buildTraceroutePolylines(forwardOffsetPoints, returnOffsetPoints, density).forEach { map.overlays.add(it) }

            // Render simple node markers
            displayNodes.forEach { node ->
                val position = GeoPoint(node.latitude, node.longitude)
                val marker =
                    MarkerWithLabel(mapView = map, label = "${node.user.short_name} ${formatAgo(node.position.time)}")
                        .apply {
                            id = node.user.id
                            title = node.user.long_name
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            this.position = position
                            icon = markerIcon
                            setNodeColors(node.colors)
                        }
                map.overlays.add(marker)
            }

            map.invalidate()
        },
    )
}

private fun buildTraceroutePolylines(
    forwardPoints: List<GeoPoint>,
    returnPoints: List<GeoPoint>,
    density: androidx.compose.ui.unit.Density,
): List<Polyline> {
    val polylines = mutableListOf<Polyline>()

    fun buildPolyline(points: List<GeoPoint>, color: Int, strokeWidth: Float): Polyline = Polyline().apply {
        setPoints(points)
        outlinePaint.apply {
            this.color = color
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
    }

    forwardPoints
        .takeIf { it.size >= 2 }
        ?.let { points ->
            polylines.add(buildPolyline(points, TracerouteColors.OutgoingRoute.toArgb(), with(density) { 6.dp.toPx() }))
        }
    returnPoints
        .takeIf { it.size >= 2 }
        ?.let { points ->
            polylines.add(buildPolyline(points, TracerouteColors.ReturnRoute.toArgb(), with(density) { 5.dp.toPx() }))
        }
    return polylines
}

// --- Haversine offset math for OSMDroid (no SphericalUtil available) ---

private fun Double.toRad(): Double = this * PI / 180.0

private fun bearingRad(from: GeoPoint, to: GeoPoint): Double {
    val lat1 = from.latitude.toRad()
    val lat2 = to.latitude.toRad()
    val dLon = (to.longitude - from.longitude).toRad()
    return atan2(sin(dLon) * cos(lat2), cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon))
}

private fun GeoPoint.offsetPoint(headingRad: Double, offsetMeters: Double): GeoPoint {
    val distanceByRadius = offsetMeters / EARTH_RADIUS_METERS
    val lat1 = latitude.toRad()
    val lon1 = longitude.toRad()
    val lat2 = asin(sin(lat1) * cos(distanceByRadius) + cos(lat1) * sin(distanceByRadius) * cos(headingRad))
    val lon2 =
        lon1 + atan2(sin(headingRad) * sin(distanceByRadius) * cos(lat1), cos(distanceByRadius) - sin(lat1) * sin(lat2))
    return GeoPoint(lat2 * 180.0 / PI, lon2 * 180.0 / PI)
}

private fun offsetPolyline(
    points: List<GeoPoint>,
    offsetMeters: Double,
    headingReferencePoints: List<GeoPoint> = points,
    sideMultiplier: Double = 1.0,
): List<GeoPoint> {
    val headingPoints = headingReferencePoints.takeIf { it.size >= 2 } ?: points
    if (points.size < 2 || headingPoints.size < 2 || offsetMeters == 0.0) return points

    val headings =
        headingPoints.mapIndexed { index, _ ->
            when (index) {
                0 -> bearingRad(headingPoints[0], headingPoints[1])

                headingPoints.lastIndex ->
                    bearingRad(headingPoints[headingPoints.lastIndex - 1], headingPoints[headingPoints.lastIndex])

                else -> bearingRad(headingPoints[index - 1], headingPoints[index + 1])
            }
        }

    return points.mapIndexed { index, point ->
        val heading = headings[index.coerceIn(0, headings.lastIndex)]
        val perpendicularHeading = heading + (PI / 2 * sideMultiplier)
        point.offsetPoint(perpendicularHeading, abs(offsetMeters))
    }
}

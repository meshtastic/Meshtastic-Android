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

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.SphericalUtil
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.google.maps.android.compose.widgets.ScaleBar
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.app.map.toLatLng
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.theme.TracerouteColors
import org.meshtastic.feature.map.tracerouteNodeSelection
import org.meshtastic.proto.Position
import kotlin.math.abs
import kotlin.math.max

private const val TRACEROUTE_OFFSET_METERS = 100.0
private const val TRACEROUTE_BOUNDS_PADDING_PX = 120
private const val DEFAULT_ZOOM = 12f

/**
 * A focused Google Maps composable that renders **only** traceroute visualization — node markers for each hop and
 * forward/return offset polylines with auto-centering camera.
 *
 * Unlike [org.meshtastic.app.map.MapView], this composable does **not** include node clusters, waypoints, location
 * tracking, custom tile providers, map layers, or waypoint editing. It is designed to be embedded inside the
 * [TracerouteMapScreen]'s scaffold.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun TracerouteGoogleMap(
    tracerouteOverlay: TracerouteOverlay?,
    tracerouteNodePositions: Map<Int, Position>,
    onMappableCountChanged: (shown: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel = koinViewModel(),
) {
    val allNodes by mapViewModel.nodes.collectAsStateWithLifecycle(listOf())
    val theme by mapViewModel.theme.collectAsStateWithLifecycle()

    val dark =
        when (theme) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> isSystemInDarkTheme()
        }
    val mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT

    // Resolve which nodes to display for the traceroute
    val tracerouteSelection =
        remember(tracerouteOverlay, tracerouteNodePositions, allNodes) {
            mapViewModel.tracerouteNodeSelection(
                tracerouteOverlay = tracerouteOverlay,
                tracerouteNodePositions = tracerouteNodePositions,
                nodes = allNodes,
            )
        }
    val displayNodes = tracerouteSelection.nodesForMarkers

    // Report mappable count
    LaunchedEffect(tracerouteOverlay, displayNodes) {
        if (tracerouteOverlay != null) {
            onMappableCountChanged(displayNodes.size, tracerouteOverlay.relatedNodeNums.size)
        }
    }

    // Compute polyline points from node positions
    val nodeLookup = tracerouteSelection.nodeLookup
    val tracerouteForwardPoints =
        remember(tracerouteOverlay, nodeLookup) {
            tracerouteOverlay?.forwardRoute?.mapNotNull { nodeLookup[it]?.toLatLng() } ?: emptyList()
        }
    val tracerouteReturnPoints =
        remember(tracerouteOverlay, nodeLookup) {
            tracerouteOverlay?.returnRoute?.mapNotNull { nodeLookup[it]?.toLatLng() } ?: emptyList()
        }

    // Compute offset polylines for visual separation
    val tracerouteHeadingReferencePoints =
        remember(tracerouteForwardPoints, tracerouteReturnPoints) {
            when {
                tracerouteForwardPoints.size >= 2 -> tracerouteForwardPoints
                tracerouteReturnPoints.size >= 2 -> tracerouteReturnPoints
                else -> emptyList()
            }
        }
    val forwardOffsetPoints =
        remember(tracerouteForwardPoints, tracerouteHeadingReferencePoints) {
            offsetPolyline(
                points = tracerouteForwardPoints,
                offsetMeters = TRACEROUTE_OFFSET_METERS,
                headingReferencePoints = tracerouteHeadingReferencePoints,
                sideMultiplier = 1.0,
            )
        }
    val returnOffsetPoints =
        remember(tracerouteReturnPoints, tracerouteHeadingReferencePoints) {
            offsetPolyline(
                points = tracerouteReturnPoints,
                offsetMeters = TRACEROUTE_OFFSET_METERS,
                headingReferencePoints = tracerouteHeadingReferencePoints,
                sideMultiplier = -1.0,
            )
        }

    // Camera auto-center on traceroute bounds
    val cameraPositionState = rememberCameraPositionState()
    var hasCentered by remember(tracerouteOverlay) { mutableStateOf(false) }

    LaunchedEffect(tracerouteOverlay, tracerouteForwardPoints, tracerouteReturnPoints) {
        if (tracerouteOverlay == null || hasCentered) return@LaunchedEffect
        val allPoints = (tracerouteForwardPoints + tracerouteReturnPoints).distinct()
        if (allPoints.isNotEmpty()) {
            val cameraUpdate =
                if (allPoints.size == 1) {
                    CameraUpdateFactory.newLatLngZoom(
                        allPoints.first(),
                        max(cameraPositionState.position.zoom, DEFAULT_ZOOM),
                    )
                } else {
                    val bounds = LatLngBounds.builder()
                    allPoints.forEach { bounds.include(it) }
                    CameraUpdateFactory.newLatLngBounds(bounds.build(), TRACEROUTE_BOUNDS_PADDING_PX)
                }
            try {
                cameraPositionState.animate(cameraUpdate)
                hasCentered = true
            } catch (e: IllegalStateException) {
                Logger.d { "Error centering traceroute overlay: ${e.message}" }
            }
        }
    }

    Box(modifier = modifier) {
        GoogleMap(
            mapColorScheme = mapColorScheme,
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings =
            MapUiSettings(
                zoomControlsEnabled = true,
                mapToolbarEnabled = false,
                compassEnabled = false,
                myLocationButtonEnabled = false,
                rotationGesturesEnabled = true,
                scrollGesturesEnabled = true,
                tiltGesturesEnabled = false,
                zoomGesturesEnabled = true,
            ),
        ) {
            // Forward route polyline
            if (tracerouteForwardPoints.size >= 2) {
                Polyline(
                    points = forwardOffsetPoints,
                    jointType = JointType.ROUND,
                    color = TracerouteColors.OutgoingRoute,
                    width = 9f,
                    zIndex = 3.0f,
                )
            }
            // Return route polyline
            if (tracerouteReturnPoints.size >= 2) {
                Polyline(
                    points = returnOffsetPoints,
                    jointType = JointType.ROUND,
                    color = TracerouteColors.ReturnRoute,
                    width = 7f,
                    zIndex = 2.5f,
                )
            }

            // Individual node markers (simple markers, no clustering needed for traceroute)
            displayNodes.forEach { node ->
                val markerState = rememberUpdatedMarkerState(position = node.position.toLatLng())
                MarkerComposable(state = markerState, zIndex = 4f) { NodeChip(node = node) }
            }
        }

        ScaleBar(
            cameraPositionState = cameraPositionState,
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp),
        )
    }
}

private fun Node.toLatLng(): LatLng? = this.position.toLatLng()

private fun offsetPolyline(
    points: List<LatLng>,
    offsetMeters: Double,
    headingReferencePoints: List<LatLng> = points,
    sideMultiplier: Double = 1.0,
): List<LatLng> {
    val headingPoints = headingReferencePoints.takeIf { it.size >= 2 } ?: points
    if (points.size < 2 || headingPoints.size < 2 || offsetMeters == 0.0) return points

    val headings =
        headingPoints.mapIndexed { index, _ ->
            when (index) {
                0 -> SphericalUtil.computeHeading(headingPoints[0], headingPoints[1])
                headingPoints.lastIndex ->
                    SphericalUtil.computeHeading(
                        headingPoints[headingPoints.lastIndex - 1],
                        headingPoints[headingPoints.lastIndex],
                    )

                else -> SphericalUtil.computeHeading(headingPoints[index - 1], headingPoints[index + 1])
            }
        }

    return points.mapIndexed { index, point ->
        val heading = headings[index.coerceIn(0, headings.lastIndex)]
        val perpendicularHeading = heading + (90.0 * sideMultiplier)
        SphericalUtil.computeOffset(point, abs(offsetMeters), perpendicularHeading)
    }
}

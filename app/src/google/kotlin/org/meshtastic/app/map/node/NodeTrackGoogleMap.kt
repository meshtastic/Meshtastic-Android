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

package org.meshtastic.app.map.node

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerInfoWindowComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.google.maps.android.compose.widgets.ScaleBar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.app.map.component.MapButton
import org.meshtastic.app.map.component.NodeMapFilterDropdown
import org.meshtastic.app.map.toLatLng
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.metersIn
import org.meshtastic.core.model.util.mpsToKmph
import org.meshtastic.core.model.util.mpsToMph
import org.meshtastic.core.model.util.toString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.alt
import org.meshtastic.core.resources.heading
import org.meshtastic.core.resources.latitude
import org.meshtastic.core.resources.longitude
import org.meshtastic.core.resources.map_filter
import org.meshtastic.core.resources.position
import org.meshtastic.core.resources.sats
import org.meshtastic.core.resources.speed
import org.meshtastic.core.resources.timestamp
import org.meshtastic.core.resources.track_point
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.TripOrigin
import org.meshtastic.core.ui.icon.Tune
import org.meshtastic.core.ui.util.formatAgo
import org.meshtastic.core.ui.util.formatPositionTime
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import org.meshtastic.proto.Position
import kotlin.math.max

private const val DEG_D = 1e-7
private const val HEADING_DEG = 1e-5
private const val TRACK_BOUNDS_PADDING_PX = 80
private const val DEFAULT_ZOOM = 12f

/**
 * A focused Google Maps composable that renders **only** a node's position track — polyline segments with
 * gradient-alpha markers and an info-window for each historical position.
 *
 * Unlike [org.meshtastic.app.map.MapView], this composable does **not** include node clusters, waypoints, location
 * tracking, custom tile providers, map layers, or waypoint editing. It is designed to be embedded inside the
 * position-log adaptive layout.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NodeTrackGoogleMap(
    focusedNode: Node?,
    positions: List<Position>,
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel = koinViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val displayUnits by mapViewModel.displayUnits.collectAsStateWithLifecycle()
    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val theme by mapViewModel.theme.collectAsStateWithLifecycle()
    val myNodeNum = mapViewModel.myNodeNum

    val dark =
        when (theme) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> isSystemInDarkTheme()
        }
    val mapColorScheme = if (dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT

    // Apply track time filter
    val lastHeardTrackFilter = mapFilterState.lastHeardTrackFilter
    val timeFilteredPositions =
        remember(positions, lastHeardTrackFilter) {
            positions.filter {
                lastHeardTrackFilter == LastHeardFilter.Any || it.time > nowSeconds - lastHeardTrackFilter.seconds
            }
        }
    val sortedPositions = remember(timeFilteredPositions) { timeFilteredPositions.sortedBy { it.time } }

    // Camera state — centers on track bounds
    val cameraPositionState = rememberCameraPositionState()
    var hasCentered by remember(positions) { mutableStateOf(false) }

    LaunchedEffect(sortedPositions, hasCentered) {
        if (hasCentered || sortedPositions.isEmpty()) return@LaunchedEffect
        val points = sortedPositions.map { it.toLatLng() }
        val cameraUpdate =
            if (points.size == 1) {
                CameraUpdateFactory.newLatLngZoom(points.first(), max(cameraPositionState.position.zoom, DEFAULT_ZOOM))
            } else {
                val bounds = LatLngBounds.builder()
                points.forEach { bounds.include(it) }
                CameraUpdateFactory.newLatLngBounds(bounds.build(), TRACK_BOUNDS_PADDING_PX)
            }
        try {
            cameraPositionState.animate(cameraUpdate)
            hasCentered = true
        } catch (e: IllegalStateException) {
            Logger.d { "Error centering track map: ${e.message}" }
        }
    }

    // Track filter controls
    var filterMenuExpanded by remember { mutableStateOf(false) }

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
            if (focusedNode != null && sortedPositions.isNotEmpty()) {
                NodeTrackOverlay(
                    focusedNode = focusedNode,
                    sortedPositions = sortedPositions,
                    displayUnits = displayUnits,
                    myNodeNum = myNodeNum,
                )
            }
        }

        // Scale bar
        ScaleBar(
            cameraPositionState = cameraPositionState,
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp),
        )

        // Track filter button
        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
            MapButton(
                icon = MeshtasticIcons.Tune,
                contentDescription = stringResource(Res.string.map_filter),
                onClick = { filterMenuExpanded = true },
            )
            NodeMapFilterDropdown(
                expanded = filterMenuExpanded,
                onDismissRequest = { filterMenuExpanded = false },
                mapViewModel = mapViewModel,
            )
        }
    }
}

/**
 * Renders the position track polyline segments and markers inside a [GoogleMap] content scope. Each marker fades from
 * transparent (oldest) to opaque (newest). The newest position shows the node's [NodeChip]; older positions show a
 * [TripOrigin] dot with an info-window on tap.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun NodeTrackOverlay(
    focusedNode: Node,
    sortedPositions: List<Position>,
    displayUnits: DisplayUnits,
    myNodeNum: Int?,
) {
    val isHighPriority = focusedNode.num == myNodeNum || focusedNode.isFavorite
    val activeNodeZIndex = if (isHighPriority) 5f else 4f

    sortedPositions.forEachIndexed { index, position ->
        key(position.time) {
            val markerState = rememberUpdatedMarkerState(position = position.toLatLng())
            val alpha =
                if (sortedPositions.size > 1) {
                    index.toFloat() / (sortedPositions.size.toFloat() - 1)
                } else {
                    1f
                }
            val color = Color(focusedNode.colors.second).copy(alpha = alpha)

            if (index == sortedPositions.lastIndex) {
                MarkerComposable(
                    state = markerState,
                    zIndex = activeNodeZIndex,
                    alpha = if (isHighPriority) 1.0f else 0.9f,
                ) {
                    NodeChip(node = focusedNode)
                }
            } else {
                MarkerInfoWindowComposable(
                    state = markerState,
                    title = stringResource(Res.string.position),
                    snippet = formatAgo(position.time),
                    zIndex = 1f + alpha,
                    infoContent = { PositionInfoWindowContent(position = position, displayUnits = displayUnits) },
                ) {
                    Icon(
                        imageVector = MeshtasticIcons.TripOrigin,
                        contentDescription = stringResource(Res.string.track_point),
                        tint = color,
                    )
                }
            }
        }
    }

    // Gradient polyline segments
    if (sortedPositions.size > 1) {
        val segments = sortedPositions.windowed(size = 2, step = 1, partialWindows = false)
        segments.forEachIndexed { index, segmentPoints ->
            val alpha = index.toFloat() / (segments.size.toFloat() - 1)
            Polyline(
                points = segmentPoints.map { it.toLatLng() },
                jointType = JointType.ROUND,
                color = Color(focusedNode.colors.second).copy(alpha = alpha),
                width = 8f,
                zIndex = 0.6f,
            )
        }
    }
}

/**
 * Info-window content for a position track marker. Shows latitude, longitude, satellites, altitude, speed, heading, and
 * timestamp.
 */
@Composable
internal fun PositionInfoWindowContent(position: Position, displayUnits: DisplayUnits = DisplayUnits.METRIC) {
    @Composable
    fun PositionRow(label: String, value: String) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Text(value, style = MaterialTheme.typography.labelMedium)
        }
    }

    Card {
        Column(modifier = Modifier.padding(8.dp)) {
            PositionRow(
                label = stringResource(Res.string.latitude),
                value = "%.5f".format((position.latitude_i ?: 0) * DEG_D),
            )
            PositionRow(
                label = stringResource(Res.string.longitude),
                value = "%.5f".format((position.longitude_i ?: 0) * DEG_D),
            )
            PositionRow(label = stringResource(Res.string.sats), value = position.sats_in_view.toString())
            PositionRow(
                label = stringResource(Res.string.alt),
                value = (position.altitude ?: 0).metersIn(displayUnits).toString(displayUnits),
            )
            PositionRow(label = stringResource(Res.string.speed), value = speedFromPosition(position, displayUnits))
            PositionRow(
                label = stringResource(Res.string.heading),
                value = "%.0f°".format((position.ground_track ?: 0) * HEADING_DEG),
            )
            PositionRow(label = stringResource(Res.string.timestamp), value = position.formatPositionTime())
        }
    }
}

@Composable
private fun speedFromPosition(position: Position, displayUnits: DisplayUnits): String {
    val speedInMps = position.ground_speed ?: 0
    val mpsText = "%d m/s".format(speedInMps)
    return if (speedInMps > 10) {
        when (displayUnits) {
            DisplayUnits.METRIC -> "%.1f Km/h".format(speedInMps.mpsToKmph())
            DisplayUnits.IMPERIAL -> "%.1f mph".format(speedInMps.mpsToMph())
            else -> mpsText
        }
    } else {
        mpsText
    }
}

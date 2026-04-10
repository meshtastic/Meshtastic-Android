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
package org.meshtastic.app.map.node

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.map.MapViewModel
import org.meshtastic.app.map.addCopyright
import org.meshtastic.app.map.addPolyline
import org.meshtastic.app.map.addPositionMarkers
import org.meshtastic.app.map.addScaleBarOverlay
import org.meshtastic.app.map.component.MapControlsOverlay
import org.meshtastic.app.map.model.CustomTileSource
import org.meshtastic.app.map.rememberMapViewWithLifecycle
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.last_heard_filter_label
import org.meshtastic.feature.map.LastHeardFilter
import org.meshtastic.proto.Position
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import kotlin.math.roundToInt

/**
 * A focused OSMDroid map composable that renders **only** a node's position track — a dashed polyline with directional
 * markers for each historical position.
 *
 * Applies the [lastHeardTrackFilter][org.meshtastic.feature.map.BaseMapViewModel.MapFilterState.lastHeardTrackFilter]
 * from [MapViewModel] to filter positions by time, matching the behavior of the Google Maps implementation. Includes a
 * track filter slider UI so users can adjust the time range directly from the map.
 *
 * Unlike the monolithic [org.meshtastic.app.map.MapView], this composable does **not** include node clusters,
 * waypoints, location tracking, or any map controls. It is designed to be embedded inside the position-log adaptive
 * layout.
 */
@Composable
fun NodeTrackOsmMap(
    positions: List<Position>,
    applicationId: String,
    mapStyleId: Int,
    modifier: Modifier = Modifier,
    mapViewModel: MapViewModel = koinViewModel(),
) {
    val density = LocalDensity.current
    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val lastHeardTrackFilter = mapFilterState.lastHeardTrackFilter

    val filteredPositions =
        remember(positions, lastHeardTrackFilter) {
            positions.filter {
                lastHeardTrackFilter == LastHeardFilter.Any || it.time > nowSeconds - lastHeardTrackFilter.seconds
            }
        }

    val geoPoints =
        remember(filteredPositions) {
            filteredPositions.map { GeoPoint((it.latitude_i ?: 0) * DEG_D, (it.longitude_i ?: 0) * DEG_D) }
        }
    val cameraView = remember(geoPoints) { BoundingBox.fromGeoPoints(geoPoints) }
    val mapView =
        rememberMapViewWithLifecycle(
            applicationId = applicationId,
            box = cameraView,
            tileSource = CustomTileSource.getTileSource(mapStyleId),
        )

    var filterMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { mapView },
            update = { map ->
                map.overlays.clear()
                map.addCopyright()
                map.addScaleBarOverlay(density)
                map.addPolyline(density, geoPoints) {}
                map.addPositionMarkers(filteredPositions) {}
            },
        )

        // Track filter controls overlay
        MapControlsOverlay(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            onToggleFilterMenu = { filterMenuExpanded = true },
            filterDropdownContent = {
                DropdownMenu(expanded = filterMenuExpanded, onDismissRequest = { filterMenuExpanded = false }) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val filterOptions = LastHeardFilter.entries
                        val selectedIndex = filterOptions.indexOf(lastHeardTrackFilter)
                        var sliderPosition by remember(selectedIndex) { mutableFloatStateOf(selectedIndex.toFloat()) }

                        Text(
                            text =
                            stringResource(
                                Res.string.last_heard_filter_label,
                                stringResource(lastHeardTrackFilter.label),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            onValueChangeFinished = {
                                val newIndex = sliderPosition.roundToInt().coerceIn(0, filterOptions.size - 1)
                                mapViewModel.setLastHeardTrackFilter(filterOptions[newIndex])
                            },
                            valueRange = 0f..(filterOptions.size - 1).toFloat(),
                            steps = filterOptions.size - 2,
                        )
                    }
                }
            },
        )
    }
}

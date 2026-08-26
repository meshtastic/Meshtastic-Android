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
package org.meshtastic.feature.map.maplibre.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraState
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.maplibre.ZOOM_STEP
import org.meshtastic.feature.map.maplibre.style.Basemaps
import org.meshtastic.feature.map.maplibre.style.zoomRange
import org.meshtastic.feature.map.maplibre.zoomBy

/**
 * Toolbar for the node-track map: compass, zoom and the track's own age filter.
 *
 * The same floating toolbar the main map uses, so the two read as one control language, but with only the buttons a
 * track map can honour. Location tracking is absent — the track map has no location plumbing, and a button that does
 * nothing is worse than no button. Basemap selection is absent for now, and is the remaining gap against both
 * predecessors here.
 */
@Composable
internal fun TrackMapControls(cameraState: CameraState, modifier: Modifier = Modifier) {
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val zoomRange = Basemaps.default.zoomRange()

    MapControlsOverlay(
        modifier = modifier,
        onToggleFilterMenu = { filterMenuExpanded = !filterMenuExpanded },
        filterDropdownContent = {
            TrackFilterMenu(expanded = filterMenuExpanded, onDismissRequest = { filterMenuExpanded = false })
        },
        bearing = cameraState.position.bearing.toFloat(),
        onCompassClick = { scope.launch { cameraState.animateTo(cameraState.position.copy(bearing = 0.0)) } },
        onZoomIn = { scope.launch { cameraState.zoomBy(ZOOM_STEP, zoomRange) } },
        onZoomOut = { scope.launch { cameraState.zoomBy(-ZOOM_STEP, zoomRange) } },
        onToggleLocationTracking = null,
    )
}

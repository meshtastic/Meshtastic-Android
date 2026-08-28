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

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraState
import org.meshtastic.feature.map.component.MapZoomControls
import org.meshtastic.feature.map.maplibre.ZOOM_STEP
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.zoomRange
import org.meshtastic.feature.map.maplibre.zoomBy

/** Inset from the trailing edge, matching the toolbar's own inset from the top. */
private const val ZOOM_INSET = 8

/**
 * Lift above the attribution button, which is 40dp of icon plus its pill padding and shares this corner. More than the
 * cards need on the other side, where only the 23dp wordmark sits.
 */
private const val ZOOM_BOTTOM_INSET = 56

/**
 * Zoom controls in the lower trailing corner of a map, where Google Maps draws its own.
 *
 * Every MapLibre surface calls this, so the control sits in the same place on all of them. Written as a [BoxScope]
 * extension because the placement is the point: a caller that had to align it itself would eventually align it
 * somewhere else.
 *
 * Lifted clear of the logo and attribution row along the bottom edge, which the styles are licensed on condition of
 * showing — see [MeshMapOrnaments].
 */
@Composable
internal fun BoxScope.MapZoom(cameraState: CameraState, basemap: Basemap) {
    val scope = rememberCoroutineScope()
    val zoomRange = basemap.zoomRange()

    MapZoomControls(
        onZoomIn = { scope.launch { cameraState.zoomBy(ZOOM_STEP, zoomRange) } },
        onZoomOut = { scope.launch { cameraState.zoomBy(-ZOOM_STEP, zoomRange) } },
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = ZOOM_INSET.dp, bottom = ZOOM_BOTTOM_INSET.dp),
    )
}

/**
 * Lift for the compact pair. The mini-map keeps MapLibre's default ornaments, so the attribution button still shares
 * this corner; this clears it without the extra room the full-size control needs.
 */
private const val COMPACT_ZOOM_BOTTOM_INSET = 44

/**
 * The same controls, sized for the node-detail mini-map.
 *
 * That map is 200dp tall, so the full-size pair would take up half of it — which is why it shipped with no zoom control
 * at all. The Google flavor does show one there (`MapUiSettings(zoomControlsEnabled = true)` in its own `InlineMap`),
 * and in fact shows *only* that: it disables the zoom and scroll gestures outright. This map keeps its gestures and
 * adds the buttons, so it ends up with both ways in rather than one.
 */
@Composable
internal fun BoxScope.MapZoomCompact(cameraState: CameraState, basemap: Basemap) {
    val scope = rememberCoroutineScope()
    val zoomRange = basemap.zoomRange()

    MapZoomControls(
        onZoomIn = { scope.launch { cameraState.zoomBy(ZOOM_STEP, zoomRange) } },
        onZoomOut = { scope.launch { cameraState.zoomBy(-ZOOM_STEP, zoomRange) } },
        modifier =
        Modifier.align(Alignment.BottomEnd).padding(end = ZOOM_INSET.dp, bottom = COMPACT_ZOOM_BOTTOM_INSET.dp),
        compact = true,
    )
}

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
package org.meshtastic.feature.map.maplibre

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.MapState
import org.maplibre.spatialk.geojson.BoundingBox

/**
 * Steps the camera zoom by [delta], clamped to [range].
 *
 * Shared by the main map's toolbar and the node-track map's. MapLibre publishes no zoom ornament, so every map that
 * wants buttons has to do this itself, and the clamp matters: pushing past a source's maximum zoom leaves the renderer
 * with no tiles to draw.
 */
internal suspend fun MapState.zoomBy(delta: Double, range: ClosedFloatingPointRange<Float>) {
    val target = (cameraPosition.zoom + delta).coerceIn(range.start.toDouble(), range.endInclusive.toDouble())
    if (target != cameraPosition.zoom) animateCameraPosition(cameraPosition.copy(zoom = target))
}

/** One zoom level per button press, which is what both predecessors' zoom controls did. */
internal const val ZOOM_STEP = 1.0

/**
 * Frames [bounds] without zooming past [FRAME_MAX_ZOOM]: the fit is computed first and capped before the camera moves,
 * so nodes standing metres apart open on their surroundings rather than on empty tiles. Eased, not the default flight.
 */
internal suspend fun MapState.frameBounds(bounds: BoundingBox, padding: PaddingValues = PaddingValues(0.dp)) {
    val fitted = cameraForBounds(bounds, padding = padding)
    animateCameraPosition(fitted.cappedTo(FRAME_MAX_ZOOM), CameraAnimation.Ease())
}

/** This position, zoomed out to [maxZoom] if it is tighter than that. */
internal fun CameraPosition.cappedTo(maxZoom: Double): CameraPosition =
    if (zoom > maxZoom) copy(zoom = maxZoom) else this

/** The zoom a map opens at on a single point of interest; framing a mesh never goes tighter than that. */
internal const val FRAME_MAX_ZOOM = DETAIL_ZOOM

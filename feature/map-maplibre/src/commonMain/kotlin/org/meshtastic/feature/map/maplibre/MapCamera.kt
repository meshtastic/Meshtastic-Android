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

import org.maplibre.compose.camera.CameraState

/**
 * Steps the camera zoom by [delta], clamped to [range].
 *
 * Shared by the main map's toolbar and the node-track map's. MapLibre publishes no zoom ornament, so every map that
 * wants buttons has to do this itself, and the clamp matters: pushing past a source's maximum zoom leaves the renderer
 * with no tiles to draw.
 */
internal suspend fun CameraState.zoomBy(delta: Double, range: ClosedFloatingPointRange<Float>) {
    val target = (position.zoom + delta).coerceIn(range.start.toDouble(), range.endInclusive.toDouble())
    if (target != position.zoom) animateTo(position.copy(zoom = target))
}

/** One zoom level per button press, which is what both predecessors' zoom controls did. */
internal const val ZOOM_STEP = 1.0

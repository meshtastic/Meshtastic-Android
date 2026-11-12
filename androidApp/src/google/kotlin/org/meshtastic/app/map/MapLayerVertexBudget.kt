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
package org.meshtastic.app.map

import com.google.maps.android.data.renderer.model.DataLayer
import com.google.maps.android.data.renderer.model.Geometry
import com.google.maps.android.data.renderer.model.LineString
import com.google.maps.android.data.renderer.model.MultiGeometry
import com.google.maps.android.data.renderer.model.Polygon

/**
 * How many vertices imported layers may put on the Google map at once.
 *
 * The Maps SDK documents no shape or vertex limit and draws every shape from the app's own heap: a vertex is held as a
 * model point, again as a `LatLng` in the shape's options, and again inside the SDK. With no ceiling, a handful of
 * dense layers fills the heap within seconds of the map opening, and the allocation that finally fails is on an SDK
 * thread where nothing can catch it. So the ceiling is a share of the heap, checked before a layer is drawn.
 */
internal class MapLayerVertexBudget(val perLayer: Int, val total: Int) {

    private var held = 0

    /** Vertices currently drawn. */
    val inUse: Int
        @Synchronized get() = held

    /** Reserve [vertices] for one layer; false when the layer alone, or with what is already drawn, is too much. */
    @Synchronized
    fun tryAcquire(vertices: Int): Boolean {
        if (vertices > perLayer || vertices > total - held) return false
        held += vertices
        return true
    }

    @Synchronized
    fun release(vertices: Int) {
        held = (held - vertices).coerceAtLeast(0)
    }

    companion object {
        /** An eighth of the heap for everything drawn, the share Android's own cache guidance starts from. */
        private const val HEAP_SHARE = 8

        // Model point, LatLng and the SDK's copy, with their list slots.
        private const val RESIDENT_BYTES_PER_VERTEX = 120L

        // While one layer loads, its JSON tree and parser coordinates are alive beside the resident copies.
        private const val LOADING_BYTES_PER_VERTEX = 300L

        fun forHeap(maxHeapBytes: Long): MapLayerVertexBudget {
            val share = maxHeapBytes / HEAP_SHARE
            return MapLayerVertexBudget(
                perLayer = (share / LOADING_BYTES_PER_VERTEX).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                total = (share / RESIDENT_BYTES_PER_VERTEX).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            )
        }
    }
}

/** Every coordinate the layer would hand to the map, holes included. */
internal fun DataLayer.vertexCount(): Int = features.sumOf { it.geometry.vertexCount() }

private fun Geometry.vertexCount(): Int = when (this) {
    is Polygon -> outerBoundary.size + innerBoundaries.sumOf { it.size }
    is LineString -> points.size
    is MultiGeometry -> geometries.sumOf { it.vertexCount() }
    else -> 1
}

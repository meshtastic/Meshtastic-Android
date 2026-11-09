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

package org.meshtastic.feature.map.terrain.spatial

import org.meshtastic.feature.map.terrain.GeoBounds
import kotlin.math.sqrt

/** The part of a mesh node the spatial map places. */
data class SpatialNodeSnapshot(val id: Long, val coordinate: LatLon, val isConnectedRadio: Boolean = false)

/** A downloaded offline map region, as the spatial map's coverage checks see it. */
data class SpatialOfflineRegion(
    val id: String,
    val bounds: GeoBounds,
    val hasTerrain: Boolean,
    val hasBasemap: Boolean,
    /** Which feature keeps this region, null for the user's own downloads. */
    val managedBy: String? = null,
    val createdMillis: Long = 0,
    val totalByteCount: Long = 0,
)

/**
 * Which circle the spatial map shows. The circle is rebuilt only when it has to be: a node outside it, or the mesh
 * having shrunk well inside it, so the terrain is not rebuilt on every refresh.
 */
object SpatialMapArea {
    /**
     * Nodes farther than this from the anchor are left out: a mesh with MQTT nodes on another continent cannot be a
     * one-meter model.
     */
    const val MAX_RADIUS_METERS = 125_000.0
    const val SHRINK_RATIO = 0.5
    const val MINIMUM_REBUILD_INTERVAL_MILLIS = 30_000L

    data class Selection(val coordinates: List<LatLon>, val outsideCount: Int)

    private fun flatDistance(frame: SpatialFrame, coordinate: LatLon): Double {
        val p = frame.localPosition(coordinate, 0.0)
        return sqrt((p.x * p.x + p.z * p.z).toDouble())
    }

    private fun frameAt(center: LatLon) =
        SpatialFrame(GeoBounds(center.latitude, center.longitude, center.latitude, center.longitude))

    /** The coordinates that fit around the anchor (the connected radio, else the centroid). */
    fun select(coordinates: List<LatLon>, anchor: LatLon?): Selection {
        if (coordinates.isEmpty()) return Selection(emptyList(), 0)
        val center =
            anchor
                ?: LatLon(
                    coordinates.sumOf { it.latitude } / coordinates.size,
                    coordinates.sumOf { it.longitude } / coordinates.size,
                )
        val frame = frameAt(center)
        val inside = coordinates.filter { flatDistance(frame, it) <= MAX_RADIUS_METERS }
        return Selection(inside, coordinates.size - inside.size)
    }

    /**
     * With no node positions, the circle around the newest offline map the user downloaded, so the table shows that
     * place. The map the app keeps for the mesh itself is left out.
     */
    fun downloadedMapCircle(regions: List<SpatialOfflineRegion>): SpatialCircle? {
        val newest = regions.filter { it.managedBy == null }.maxByOrNull { it.createdMillis } ?: return null
        val corners =
            listOf(LatLon(newest.bounds.south, newest.bounds.west), LatLon(newest.bounds.north, newest.bounds.east))
        return SpatialCircle.around(corners, padding = 0.0)?.let {
            SpatialCircle(it.latitude, it.longitude, minOf(it.radiusMeters, MAX_RADIUS_METERS))
        }
    }

    /** The circle to use next: null to keep the current one. */
    fun next(
        current: SpatialCircle?,
        candidate: SpatialCircle,
        nodes: List<LatLon>,
        lastChangeMillis: Long?,
        nowMillis: Long,
    ): SpatialCircle? = when {
        current == null -> candidate

        nodes.any { !current.contains(it) } -> candidate

        candidate.radiusMeters < current.radiusMeters * SHRINK_RATIO &&
            (lastChangeMillis == null || nowMillis - lastChangeMillis >= MINIMUM_REBUILD_INTERVAL_MILLIS) ->
            candidate

        else -> null
    }

    /** The nodes the map shows and how many were left out. */
    data class Fitted(val nodes: List<SpatialNodeSnapshot>, val outsideCount: Int)

    /**
     * The nodes the map shows: when some are too far from the connected radio, those are left out, except [pinned]
     * ones. With no connected radio every node stays.
     */
    fun fit(nodes: List<SpatialNodeSnapshot>, pinned: Set<Long> = emptySet()): Fitted {
        val anchor = nodes.firstOrNull { it.isConnectedRadio }?.coordinate
        if (anchor == null || select(nodes.map { it.coordinate }, anchor).outsideCount == 0) return Fitted(nodes, 0)
        val frame = frameAt(anchor)
        val kept = nodes.filter { it.id in pinned || flatDistance(frame, it.coordinate) <= MAX_RADIUS_METERS }
        return Fitted(kept, nodes.size - kept.size)
    }

    /** The circle around the whole mesh, before any zoom: what terrain downloads cover. */
    fun meshCircle(nodes: List<SpatialNodeSnapshot>): SpatialCircle? =
        SpatialCircle.around(fit(nodes).nodes.map { it.coordinate })
}

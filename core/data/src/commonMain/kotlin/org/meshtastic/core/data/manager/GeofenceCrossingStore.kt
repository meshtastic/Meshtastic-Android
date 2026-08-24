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
package org.meshtastic.core.data.manager

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

/** The transition observed when a node's inside/outside state for a geofence is updated. */
enum class GeofenceTransition {
    /** First time this (waypoint, node) pair was seen — establishes a baseline, never notifies. */
    BASELINE,
    ENTERED,
    EXITED,

    /** Same side as last time — no notification. */
    UNCHANGED,
}

/**
 * In-memory inside/outside state per `(waypointId, nodeNum)`. NOT persisted: a relaunch re-baselines, which is exactly
 * what prevents spurious alerts at startup. Mutated from the service scope as positions arrive, so guarded by a
 * [Mutex].
 */
@Single
class GeofenceCrossingStore {
    private val mutex = Mutex()
    private val inside = mutableMapOf<Pair<Int, Int>, Boolean>()

    /** Record [isInside] for ([waypointId], [nodeNum]) and report the transition versus the prior state. */
    suspend fun update(waypointId: Int, nodeNum: Int, isInside: Boolean): GeofenceTransition = mutex.withLock {
        val was = inside.put(waypointId to nodeNum, isInside)
        when {
            was == null -> GeofenceTransition.BASELINE
            was == isInside -> GeofenceTransition.UNCHANGED
            isInside -> GeofenceTransition.ENTERED
            else -> GeofenceTransition.EXITED
        }
    }

    /** Drop crossing state for waypoints no longer active, bounding memory growth. */
    suspend fun retainOnly(waypointIds: Set<Int>): Unit =
        mutex.withLock { inside.keys.retainAll { it.first in waypointIds } }
}

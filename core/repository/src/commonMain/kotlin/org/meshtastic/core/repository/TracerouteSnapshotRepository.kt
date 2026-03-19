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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.Flow
import org.meshtastic.proto.Position

/**
 * Repository interface for managing snapshots of traceroute results.
 */
interface TracerouteSnapshotRepository {
    /**
     * Returns a reactive flow of positions associated with a specific traceroute log.
     */
    fun getSnapshotPositions(logUuid: String): Flow<Map<Int, Position>>

    /**
     * Persists a set of positions for a traceroute log.
     */
    suspend fun upsertSnapshotPositions(logUuid: String, requestId: Int, positions: Map<Int, Position>)
}

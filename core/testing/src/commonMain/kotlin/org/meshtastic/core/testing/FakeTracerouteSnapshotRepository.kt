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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.meshtastic.core.repository.TracerouteSnapshotRepository
import org.meshtastic.proto.Position

/**
 * A test double for [TracerouteSnapshotRepository] keyed by `logUuid`.
 *
 * Use [upsertSnapshotPositions] as you would in production, or [seedSnapshot] to directly inject state for a log.
 */
class FakeTracerouteSnapshotRepository :
    BaseFake(),
    TracerouteSnapshotRepository {

    private val snapshots = mutableStateFlow<Map<String, Map<Int, Position>>>(emptyMap())
    private val requestIds = mutableMapOf<String, Int>()

    init {
        registerResetAction { requestIds.clear() }
    }

    override fun getSnapshotPositions(logUuid: String): Flow<Map<Int, Position>> =
        snapshots.map { it[logUuid].orEmpty() }

    override suspend fun upsertSnapshotPositions(logUuid: String, requestId: Int, positions: Map<Int, Position>) {
        requestIds[logUuid] = requestId
        snapshots.value = snapshots.value.toMutableMap().also { it[logUuid] = positions }
    }

    /** Directly seeds the snapshot for a log (bypasses request-id tracking). */
    fun seedSnapshot(logUuid: String, positions: Map<Int, Position>) {
        snapshots.value = snapshots.value.toMutableMap().also { it[logUuid] = positions }
    }

    /** Returns the last request-id recorded for [logUuid], or `null` if none. */
    fun lastRequestId(logUuid: String): Int? = requestIds[logUuid]
}

/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.entity.TracerouteNodePositionEntity
import org.meshtastic.proto.MeshProtos
import javax.inject.Inject

class TracerouteSnapshotRepository
@Inject
constructor(
    private val dbManager: DatabaseManager,
) {

    fun getSnapshotPositions(logUuid: String): Flow<Map<Int, MeshProtos.Position>> =
        dbManager.currentDb
            .flatMapLatest { it.tracerouteNodePositionDao().getByLogUuid(logUuid) }
            .distinctUntilChanged()
            .map { list -> list.associate { it.nodeNum to it.position } }
            .conflate()

    suspend fun upsertSnapshotPositions(
        logUuid: String,
        requestId: Int,
        positions: Map<Int, MeshProtos.Position>
    ) {
        dbManager.currentDb.value.withTransaction {
            val dao = dbManager.currentDb.value.tracerouteNodePositionDao()
            dao.deleteByLogUuid(logUuid)
            if (positions.isEmpty()) return@withTransaction
            val entities = positions.map { (nodeNum, position) ->
                TracerouteNodePositionEntity(
                    logUuid = logUuid,
                    requestId = requestId,
                    nodeNum = nodeNum,
                    position = position,
                )
            }
            dao.insertAll(entities)
        }
    }
}

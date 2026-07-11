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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.TracerouteNodePositionEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.TracerouteSnapshotRepository
import org.meshtastic.proto.Position

@Single
class TracerouteSnapshotRepositoryImpl(
    private val dbManager: DatabaseProvider,
    private val dispatchers: CoroutineDispatchers,
) : TracerouteSnapshotRepository {

    override fun getSnapshotPositions(logUuid: String): Flow<Map<Int, Position>> = dbManager.currentDb
        .flatMapLatest { it.tracerouteNodePositionDao().getByLogUuid(logUuid) }
        .distinctUntilChanged()
        .mapLatest { list -> list.associate { it.nodeNum to it.position } }
        .flowOn(dispatchers.io)
        .conflate()

    // The delete+insert pair runs in one withDb block so both land on the same DB instance and stay visible to the
    // cross-transport merge drain barrier (see DatabaseProvider).
    override suspend fun upsertSnapshotPositions(logUuid: String, requestId: Int, positions: Map<Int, Position>) {
        withContext(dispatchers.io) {
            dbManager.withDb {
                val dao = it.tracerouteNodePositionDao()
                dao.deleteByLogUuid(logUuid)
                if (positions.isEmpty()) return@withDb
                val entities =
                    positions.map { (nodeNum, position) ->
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
}

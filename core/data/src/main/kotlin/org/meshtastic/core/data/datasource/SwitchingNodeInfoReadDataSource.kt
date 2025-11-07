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

package org.meshtastic.core.data.datasource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.entity.NodeWithRelations
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwitchingNodeInfoReadDataSource @Inject constructor(private val dbManager: DatabaseManager) :
    NodeInfoReadDataSource {

    override fun myNodeInfoFlow(): Flow<MyNodeEntity?> =
        dbManager.currentDb.flatMapLatest { db -> db.nodeInfoDao().getMyNodeInfo() }

    override fun nodeDBbyNumFlow(): Flow<Map<Int, NodeWithRelations>> =
        dbManager.currentDb.flatMapLatest { db -> db.nodeInfoDao().nodeDBbyNum() }

    override fun getNodesFlow(
        sort: String,
        filter: String,
        includeUnknown: Boolean,
        hopsAwayMax: Int,
        lastHeardMin: Int,
    ): Flow<List<NodeWithRelations>> = dbManager.currentDb.flatMapLatest { db ->
        db.nodeInfoDao()
            .getNodes(
                sort = sort,
                filter = filter,
                includeUnknown = includeUnknown,
                hopsAwayMax = hopsAwayMax,
                lastHeardMin = lastHeardMin,
            )
    }

    override suspend fun getNodesOlderThan(lastHeard: Int): List<NodeEntity> =
        dbManager.withDb { it.nodeInfoDao().getNodesOlderThan(lastHeard) }

    override suspend fun getUnknownNodes(): List<NodeEntity> = dbManager.withDb { it.nodeInfoDao().getUnknownNodes() }
}

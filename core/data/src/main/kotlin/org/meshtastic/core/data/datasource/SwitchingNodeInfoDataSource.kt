/*
 * Copyright (c) 2025 Meshtastic LLC
 */

package org.meshtastic.core.data.datasource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.entity.NodeWithRelations
import org.meshtastic.core.di.CoroutineDispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwitchingNodeInfoDataSource
@Inject
constructor(
    private val dbManager: DatabaseManager,
    private val dispatchers: CoroutineDispatchers,
) : NodeInfoDataSource {

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
    ): Flow<List<NodeWithRelations>> =
        dbManager.currentDb.flatMapLatest { db ->
            db.nodeInfoDao().getNodes(
                sort = sort,
                filter = filter,
                includeUnknown = includeUnknown,
                hopsAwayMax = hopsAwayMax,
                lastHeardMin = lastHeardMin,
            )
        }

    override suspend fun upsert(node: NodeEntity) = withContext(dispatchers.io) {
        dbManager.withDb { it.nodeInfoDao().upsert(node) }
    }

    override suspend fun installConfig(mi: MyNodeEntity, nodes: List<NodeEntity>) = withContext(dispatchers.io) {
        dbManager.withDb { it.nodeInfoDao().installConfig(mi, nodes) }
    }

    override suspend fun clearNodeDB() = withContext(dispatchers.io) {
        dbManager.withDb { it.nodeInfoDao().clearNodeInfo() }
    }

    override suspend fun deleteNode(num: Int) = withContext(dispatchers.io) {
        dbManager.withDb { it.nodeInfoDao().deleteNode(num) }
    }

    override suspend fun deleteNodes(nodeNums: List<Int>) = withContext(dispatchers.io) {
        dbManager.withDb { it.nodeInfoDao().deleteNodes(nodeNums) }
    }

    override suspend fun deleteMetadata(num: Int) = withContext(dispatchers.io) {
        dbManager.withDb { it.nodeInfoDao().deleteMetadata(num) }
    }

    override suspend fun getNodesOlderThan(lastHeard: Int): List<NodeEntity> = withContext(dispatchers.io) {
        dbManager.withDb { it.nodeInfoDao().getNodesOlderThan(lastHeard) }
    }

    override suspend fun getUnknownNodes(): List<NodeEntity> = withContext(dispatchers.io) {
        dbManager.withDb { it.nodeInfoDao().getUnknownNodes() }
    }

    override suspend fun upsert(metadata: MetadataEntity) = withContext(dispatchers.io) {
        dbManager.withDb { it.nodeInfoDao().upsert(metadata) }
    }

    override suspend fun setNodeNotes(num: Int, notes: String) = withContext(dispatchers.io) {
        dbManager.withDb { it.nodeInfoDao().setNodeNotes(num, notes) }
    }
}




/*
 * Copyright (c) 2025 Meshtastic LLC
 */

package org.meshtastic.core.data.datasource

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.entity.NodeWithRelations

interface NodeInfoDataSource {
    fun myNodeInfoFlow(): Flow<MyNodeEntity?>
    fun nodeDBbyNumFlow(): Flow<Map<Int, NodeWithRelations>>
    fun getNodesFlow(
        sort: String,
        filter: String,
        includeUnknown: Boolean,
        hopsAwayMax: Int,
        lastHeardMin: Int,
    ): Flow<List<NodeWithRelations>>

    suspend fun upsert(node: NodeEntity)
    suspend fun installConfig(mi: MyNodeEntity, nodes: List<NodeEntity>)
    suspend fun clearNodeDB()
    suspend fun deleteNode(num: Int)
    suspend fun deleteNodes(nodeNums: List<Int>)
    suspend fun deleteMetadata(num: Int)
    suspend fun getNodesOlderThan(lastHeard: Int): List<NodeEntity>
    suspend fun getUnknownNodes(): List<NodeEntity>
    suspend fun upsert(metadata: MetadataEntity)
    suspend fun setNodeNotes(num: Int, notes: String)
}




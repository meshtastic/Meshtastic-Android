/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.service

import android.app.Notification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.meshtastic.core.data.datasource.NodeInfoReadDataSource
import org.meshtastic.core.data.datasource.NodeInfoWriteDataSource
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.entity.NodeWithRelations
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.TelemetryProtos

class FakeNodeInfoReadDataSource : NodeInfoReadDataSource {
    val myNodeInfo = MutableStateFlow<MyNodeEntity?>(null)
    val nodes = MutableStateFlow<Map<Int, NodeWithRelations>>(emptyMap())

    override fun myNodeInfoFlow(): Flow<MyNodeEntity?> = myNodeInfo

    override fun nodeDBbyNumFlow(): Flow<Map<Int, NodeWithRelations>> = nodes

    override fun getNodesFlow(
        sort: String,
        filter: String,
        includeUnknown: Boolean,
        hopsAwayMax: Int,
        lastHeardMin: Int,
    ): Flow<List<NodeWithRelations>> = flowOf(emptyList())

    override suspend fun getNodesOlderThan(lastHeard: Int): List<NodeEntity> = emptyList()

    override suspend fun getUnknownNodes(): List<NodeEntity> = emptyList()
}

class FakeNodeInfoWriteDataSource : NodeInfoWriteDataSource {
    override suspend fun upsert(node: NodeEntity) {}

    override suspend fun installConfig(mi: MyNodeEntity, nodes: List<NodeEntity>) {}

    override suspend fun clearNodeDB(preserveFavorites: Boolean) {}

    override suspend fun deleteNode(num: Int) {}

    override suspend fun deleteNodes(nodeNums: List<Int>) {}

    override suspend fun deleteMetadata(num: Int) {}

    override suspend fun upsert(metadata: MetadataEntity) {}

    override suspend fun setNodeNotes(num: Int, notes: String) {}

    override suspend fun backfillDenormalizedNames() {}
}

class FakeMeshServiceNotifications : MeshServiceNotifications {
    override fun clearNotifications() {}

    override fun initChannels() {}

    override fun updateServiceStateNotification(
        summaryString: String?,
        telemetry: TelemetryProtos.Telemetry?,
    ): Notification = null as Notification

    override suspend fun updateMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
        channelName: String?,
    ) {}

    override suspend fun updateWaypointNotification(
        contactKey: String,
        name: String,
        message: String,
        waypointId: Int,
    ) {}

    override suspend fun updateReactionNotification(
        contactKey: String,
        name: String,
        emoji: String,
        isBroadcast: Boolean,
        channelName: String?,
    ) {}

    override fun showAlertNotification(contactKey: String, name: String, alert: String) {}

    override fun showNewNodeSeenNotification(node: NodeEntity) {}

    override fun showOrUpdateLowBatteryNotification(node: NodeEntity, isRemote: Boolean) {}

    override fun showClientNotification(clientNotification: MeshProtos.ClientNotification) {}

    override fun cancelMessageNotification(contactKey: String) {}

    override fun cancelLowBatteryNotification(node: NodeEntity) {}

    override fun clearClientNotification(notification: MeshProtos.ClientNotification) {}
}

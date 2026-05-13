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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.data.datasource.NodeInfoReadDataSource
import org.meshtastic.core.data.datasource.NodeInfoWriteDataSource
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.datastore.LocalStatsDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.User

/** Repository for managing node-related data, including hardware info, node database, and identity. */
@Single
@Suppress("TooManyFunctions")
class NodeRepositoryImpl(
    @Named("ProcessLifecycle") private val processLifecycle: Lifecycle,
    private val nodeInfoReadDataSource: NodeInfoReadDataSource,
    private val nodeInfoWriteDataSource: NodeInfoWriteDataSource,
    private val dispatchers: CoroutineDispatchers,
    private val localStatsDataSource: LocalStatsDataSource,
) : NodeRepository {
    /** Hardware info about our local device (can be null if not connected). */
    override val myNodeInfo: StateFlow<MyNodeInfo?> =
        nodeInfoReadDataSource
            .myNodeInfoFlow()
            .map { it?.toMyNodeInfo() }
            .flowOn(dispatchers.io)
            .stateIn(processLifecycle.coroutineScope, SharingStarted.Eagerly, null)

    private val _ourNodeInfo = MutableStateFlow<Node?>(null)

    /** Information about the locally connected node, as seen from the mesh. */
    override val ourNodeInfo: StateFlow<Node?>
        get() = _ourNodeInfo

    private val _myId = MutableStateFlow<String?>(null)

    /** The unique userId (hex string) of our local node. */
    override val myId: StateFlow<String?>
        get() = _myId

    /** The latest local stats telemetry received from the locally connected node. */
    override val localStats: StateFlow<LocalStats> =
        localStatsDataSource.localStatsFlow.stateIn(
            processLifecycle.coroutineScope,
            SharingStarted.Eagerly,
            LocalStats(),
        )

    /** Update the cached local stats telemetry. */
    override fun updateLocalStats(stats: LocalStats) {
        processLifecycle.coroutineScope.launch { localStatsDataSource.setLocalStats(stats) }
    }

    /** A reactive map from nodeNum to [Node] objects, representing the entire mesh. */
    override val nodeDBbyNum: StateFlow<Map<Int, Node>> =
        nodeInfoReadDataSource
            .nodeDBbyNumFlow()
            .mapLatest { map -> map.mapValues { (_, it) -> it.toModel() } }
            .flowOn(dispatchers.io)
            .conflate()
            .stateIn(processLifecycle.coroutineScope, SharingStarted.Eagerly, emptyMap())

    init {
        // Backfill denormalized name columns for existing nodes on startup
        processLifecycle.coroutineScope.launch {
            processLifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                withContext(dispatchers.io) { nodeInfoWriteDataSource.backfillDenormalizedNames() }
            }
        }

        // Keep ourNodeInfo and myId correctly updated based on current connection and node DB
        combine(nodeDBbyNum, nodeInfoReadDataSource.myNodeInfoFlow()) { db, info -> info?.myNodeNum?.let { db[it] } }
            .onEach { node ->
                _ourNodeInfo.value = node
                _myId.value = node?.user?.id
            }
            .launchIn(processLifecycle.coroutineScope)
    }

    /**
     * Returns the node number used for log queries. Maps [nodeNum] to [MeshLog.NODE_NUM_LOCAL] (0) if it is the locally
     * connected node.
     */
    override fun effectiveLogNodeId(nodeNum: Int): Flow<Int> = nodeInfoReadDataSource
        .myNodeInfoFlow()
        .map { info -> if (nodeNum == info?.myNodeNum) MeshLog.NODE_NUM_LOCAL else nodeNum }
        .distinctUntilChanged()

    fun getNodeEntityDBbyNumFlow() =
        nodeInfoReadDataSource.nodeDBbyNumFlow().map { map -> map.mapValues { (_, it) -> it.toEntity() } }

    /** Returns the [Node] associated with a given [userId]. Falls back to a generic node if not found. */
    override fun getNode(userId: String): Node = nodeDBbyNum.value.values.find { it.user.id == userId }
        ?: Node(num = DataPacket.idToDefaultNodeNum(userId) ?: 0, user = getUser(userId))

    /** Returns the [User] info for a given [nodeNum]. */
    override fun getUser(nodeNum: Int): User = getUser(DataPacket.nodeNumToDefaultId(nodeNum))

    private val last4 = 4

    /** Returns the [User] info for a given [userId]. Falls back to a generic user if not found. */
    override fun getUser(userId: String): User {
        val found = nodeDBbyNum.value.values.find { it.user.id == userId }?.user
        if (found != null && found.long_name.isNotBlank() && found.short_name.isNotBlank()) {
            return found
        }

        val fallbackId = userId.takeLast(last4)
        val defaultLong =
            if (userId == DataPacket.ID_LOCAL) {
                ourNodeInfo.value?.user?.long_name?.takeIf { it.isNotBlank() } ?: "Local"
            } else {
                "Meshtastic $fallbackId"
            }
        val defaultShort =
            if (userId == DataPacket.ID_LOCAL) {
                ourNodeInfo.value?.user?.short_name?.takeIf { it.isNotBlank() } ?: "Local"
            } else {
                fallbackId
            }

        return found?.copy(
            long_name = found.long_name.takeIf { it.isNotBlank() } ?: defaultLong,
            short_name = found.short_name.takeIf { it.isNotBlank() } ?: defaultShort,
        ) ?: User(id = userId, long_name = defaultLong, short_name = defaultShort)
    }

    /** Returns a flow of nodes filtered and sorted according to the parameters. */
    override fun getNodes(
        sort: NodeSortOption,
        filter: String,
        includeUnknown: Boolean,
        onlyOnline: Boolean,
        onlyDirect: Boolean,
    ): Flow<List<Node>> = nodeInfoReadDataSource
        .getNodesFlow(
            sort = sort.sqlValue,
            filter = filter,
            includeUnknown = includeUnknown,
            hopsAwayMax = if (onlyDirect) 0 else -1,
            lastHeardMin = if (onlyOnline) onlineTimeThreshold() else -1,
        )
        .mapLatest { list -> list.map { it.toModel() } }
        .flowOn(dispatchers.io)
        .conflate()

    /** Upserts a [Node] to the database. */
    override suspend fun upsert(node: Node) =
        withContext(dispatchers.io) { nodeInfoWriteDataSource.upsert(node.toEntity()) }

    /** Installs initial configuration data (local info and remote nodes) into the database. */
    override suspend fun installConfig(mi: MyNodeInfo, nodes: List<Node>) = withContext(dispatchers.io) {
        nodeInfoWriteDataSource.installConfig(mi.toEntity(), nodes.map { it.toEntity() })
    }

    /** Deletes all nodes from the database, optionally preserving favorites. */
    override suspend fun clearNodeDB(preserveFavorites: Boolean) =
        withContext(dispatchers.io) { nodeInfoWriteDataSource.clearNodeDB(preserveFavorites) }

    /** Clears the local node's connection info. */
    override suspend fun clearMyNodeInfo() = withContext(dispatchers.io) { nodeInfoWriteDataSource.clearMyNodeInfo() }

    /** Deletes a node and its metadata by [num]. */
    override suspend fun deleteNode(num: Int) = withContext(dispatchers.io) {
        nodeInfoWriteDataSource.deleteNode(num)
        nodeInfoWriteDataSource.deleteMetadata(num)
    }

    /** Deletes multiple nodes and their metadata. */
    override suspend fun deleteNodes(nodeNums: List<Int>) = withContext(dispatchers.io) {
        nodeInfoWriteDataSource.deleteNodes(nodeNums)
        nodeNums.forEach { nodeInfoWriteDataSource.deleteMetadata(it) }
    }

    override suspend fun getNodesOlderThan(lastHeard: Int): List<Node> =
        withContext(dispatchers.io) { nodeInfoReadDataSource.getNodesOlderThan(lastHeard).map { it.toModel() } }

    override suspend fun getUnknownNodes(): List<Node> =
        withContext(dispatchers.io) { nodeInfoReadDataSource.getUnknownNodes().map { it.toModel() } }

    /** Persists hardware metadata for a node. */
    override suspend fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata) =
        withContext(dispatchers.io) { nodeInfoWriteDataSource.upsert(MetadataEntity(nodeNum, metadata)) }

    /** Flow emitting the count of nodes currently considered "online". */
    override val onlineNodeCount: Flow<Int> =
        nodeInfoReadDataSource
            .nodeDBbyNumFlow()
            .mapLatest { map -> map.values.count { it.node.lastHeard > onlineTimeThreshold() } }
            .flowOn(dispatchers.io)
            .conflate()

    /** Flow emitting the total number of nodes in the database. */
    override val totalNodeCount: Flow<Int> =
        nodeInfoReadDataSource
            .nodeDBbyNumFlow()
            .mapLatest { map -> map.values.count() }
            .flowOn(dispatchers.io)
            .conflate()

    override suspend fun setNodeNotes(num: Int, notes: String) =
        withContext(dispatchers.io) { nodeInfoWriteDataSource.setNodeNotes(num, notes) }

    private fun MyNodeInfo.toEntity() = MyNodeEntity(
        myNodeNum = myNodeNum,
        model = model,
        firmwareVersion = firmwareVersion,
        couldUpdate = couldUpdate,
        shouldUpdate = shouldUpdate,
        currentPacketId = currentPacketId,
        messageTimeoutMsec = messageTimeoutMsec,
        minAppVersion = minAppVersion,
        maxChannels = maxChannels,
        hasWifi = hasWifi,
        deviceId = deviceId,
        pioEnv = pioEnv,
    )

    private fun Node.toEntity() = NodeEntity(
        num = num,
        user = user,
        position = position,
        latitude = latitude,
        longitude = longitude,
        snr = snr,
        rssi = rssi,
        lastHeard = lastHeard,
        deviceTelemetry = org.meshtastic.proto.Telemetry(device_metrics = deviceMetrics),
        channel = channel,
        viaMqtt = viaMqtt,
        hopsAway = hopsAway,
        isFavorite = isFavorite,
        isIgnored = isIgnored,
        isMuted = isMuted,
        environmentTelemetry = org.meshtastic.proto.Telemetry(environment_metrics = environmentMetrics),
        powerTelemetry = org.meshtastic.proto.Telemetry(power_metrics = powerMetrics),
        paxcounter = paxcounter,
        publicKey = publicKey,
        notes = notes,
        manuallyVerified = manuallyVerified,
        nodeStatus = nodeStatus,
        lastTransport = lastTransport,
    )
}

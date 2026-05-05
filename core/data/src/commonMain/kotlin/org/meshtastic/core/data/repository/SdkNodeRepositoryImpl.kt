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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.NodeMetadataEntity
import org.meshtastic.core.datastore.LocalStatsDataSource
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.User

/**
 * SDK-backed [NodeRepository] implementation using in-memory StateFlows.
 *
 * The SDK manages node persistence internally via its SqlDelight storage layer.
 * This repository stores nodes in-memory and is populated by [NodeManager] via the
 * SDK's NodeChange flow (bridged through SdkStateBridge).
 *
 * Cold start: nodes are empty until the SDK emits its snapshot from storage (<1s).
 * Node metadata (favorites, notes, ignored, muted) persists via Room's node_metadata table.
 */
@Single(binds = [NodeRepository::class])
@Suppress("TooManyFunctions")
class SdkNodeRepositoryImpl(
    private val localStatsDataSource: LocalStatsDataSource,
    private val dbManager: DatabaseProvider,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : NodeRepository {

    private val _nodeDBbyNum = MutableStateFlow<Map<Int, Node>>(emptyMap())
    private val _myNodeInfo = MutableStateFlow<MyNodeInfo?>(null)
    private val _myNodeNum = MutableStateFlow<Int?>(null)

    // Cached metadata from Room (loaded on init, updated on writes)
    private val _metadataCache = MutableStateFlow<Map<Int, NodeMetadataEntity>>(emptyMap())

    init {
        scope.launch {
            dbManager.currentDb.flatMapLatest { db -> db.nodeMetadataDao().getAllFlow() }
                .collect { list -> _metadataCache.value = list.associateBy { it.num } }
        }
    }

    override val nodeDBbyNum: StateFlow<Map<Int, Node>> = _nodeDBbyNum

    override val myNodeInfo: StateFlow<MyNodeInfo?> = _myNodeInfo

    override val ourNodeInfo: StateFlow<Node?> =
        combine(_nodeDBbyNum, _myNodeNum) { db, myNum -> myNum?.let { db[it] } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override val myId: StateFlow<String?> =
        ourNodeInfo.map { it?.user?.id }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override val localStats: StateFlow<LocalStats> =
        localStatsDataSource.localStatsFlow
            .stateIn(scope, SharingStarted.Eagerly, LocalStats())

    override fun updateLocalStats(stats: LocalStats) {
        scope.launch { localStatsDataSource.setLocalStats(stats) }
    }

    override val onlineNodeCount: Flow<Int> =
        _nodeDBbyNum.map { map -> map.values.count { it.lastHeard > onlineTimeThreshold() } }

    override val totalNodeCount: Flow<Int> =
        _nodeDBbyNum.map { it.size }

    override fun effectiveLogNodeId(nodeNum: Int): Flow<Int> =
        _myNodeNum.map { myNum -> if (nodeNum == myNum) MeshLog.NODE_NUM_LOCAL else nodeNum }
            .distinctUntilChanged()

    override fun getNode(userId: String): Node =
        _nodeDBbyNum.value.values.find { it.user.id == userId }
            ?: Node(num = DataPacket.idToDefaultNodeNum(userId) ?: 0, user = getUser(userId))

    override fun getUser(nodeNum: Int): User = getUser(DataPacket.nodeNumToDefaultId(nodeNum))

    private val last4 = 4

    override fun getUser(userId: String): User {
        val found = _nodeDBbyNum.value.values.find { it.user.id == userId }?.user
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

    override fun getNodes(
        sort: NodeSortOption,
        filter: String,
        includeUnknown: Boolean,
        onlyOnline: Boolean,
        onlyDirect: Boolean,
    ): Flow<List<Node>> = _nodeDBbyNum.map { map ->
        map.values
            .filter { node ->
                val matchesFilter = filter.isBlank() ||
                    node.user.long_name.contains(filter, ignoreCase = true) ||
                    node.user.short_name.contains(filter, ignoreCase = true) ||
                    node.user.id.contains(filter, ignoreCase = true)
                val matchesUnknown = includeUnknown || node.user.hw_model != HardwareModel.UNSET
                val matchesOnline = !onlyOnline || node.lastHeard > onlineTimeThreshold()
                val matchesDirect = !onlyDirect || node.hopsAway == 0
                matchesFilter && matchesUnknown && matchesOnline && matchesDirect
            }
            .sortedWith(sortComparator(sort))
            .toList()
    }

    override suspend fun upsert(node: Node) {
        // Merge persisted metadata with incoming node data
        val meta = _metadataCache.value[node.num]
        val enriched = if (meta != null) {
            node.copy(
                isFavorite = meta.isFavorite,
                isIgnored = meta.isIgnored,
                isMuted = meta.isMuted,
                notes = meta.notes,
                manuallyVerified = meta.manuallyVerified,
            )
        } else {
            node
        }
        _nodeDBbyNum.update { map -> map + (enriched.num to enriched) }
    }

    override suspend fun installConfig(mi: MyNodeInfo, nodes: List<Node>) {
        _myNodeInfo.value = mi
        _myNodeNum.value = mi.myNodeNum
        _nodeDBbyNum.value = nodes.associateBy { it.num }
    }

    override suspend fun clearNodeDB(preserveFavorites: Boolean) {
        if (preserveFavorites) {
            _nodeDBbyNum.update { map -> map.filter { (_, node) -> node.isFavorite } }
        } else {
            _nodeDBbyNum.value = emptyMap()
        }
    }

    override suspend fun clearMyNodeInfo() {
        _myNodeInfo.value = null
    }

    override suspend fun deleteNode(num: Int) {
        _nodeDBbyNum.update { it - num }
        dbManager.withDb { it.nodeMetadataDao().delete(num) }
    }

    override suspend fun deleteNodes(nodeNums: List<Int>) {
        _nodeDBbyNum.update { map -> map - nodeNums.toSet() }
        dbManager.withDb { db -> nodeNums.forEach { db.nodeMetadataDao().delete(it) } }
    }

    override suspend fun getNodesOlderThan(lastHeard: Int): List<Node> =
        _nodeDBbyNum.value.values.filter { it.lastHeard < lastHeard }

    override suspend fun getUnknownNodes(): List<Node> =
        _nodeDBbyNum.value.values.filter { it.user.hw_model == HardwareModel.UNSET }

    override suspend fun setNodeNotes(num: Int, notes: String) {
        ensureMetadataExists(num)
        dbManager.withDb { it.nodeMetadataDao().setNotes(num, notes) }
        _nodeDBbyNum.update { map ->
            val node = map[num] ?: return@update map
            map + (num to node.copy(notes = notes))
        }
    }

    override suspend fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata) {
        _nodeDBbyNum.update { map ->
            val node = map[nodeNum] ?: return@update map
            map + (nodeNum to node.copy(metadata = metadata))
        }
    }

    /** Called by [NodeManager] to set the local node number for ourNodeInfo/myId derivation. */
    fun setMyNodeNum(num: Int?) {
        _myNodeNum.value = num
    }

    /** Ensures a metadata row exists for the given node, creating a default if needed. */
    private suspend fun ensureMetadataExists(num: Int) {
        if (_metadataCache.value[num] == null) {
            dbManager.withDb { it.nodeMetadataDao().upsert(NodeMetadataEntity(num = num)) }
        }
    }

    private fun sortComparator(sort: NodeSortOption): Comparator<Node> = when (sort) {
        NodeSortOption.LAST_HEARD -> compareByDescending { it.lastHeard }
        NodeSortOption.ALPHABETICAL -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.user.long_name }
        NodeSortOption.DISTANCE -> compareBy { it.hopsAway } // simplified — no GPS-based distance in POC
        NodeSortOption.HOPS_AWAY -> compareBy { it.hopsAway }
        NodeSortOption.CHANNEL -> compareBy { it.channel }
        NodeSortOption.VIA_MQTT -> compareByDescending { it.viaMqtt }
        NodeSortOption.VIA_FAVORITE -> compareByDescending { it.isFavorite }
    }
}

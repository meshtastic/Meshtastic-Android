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

import co.touchlab.kermit.Logger
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
import okio.ByteString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.NodeMetadataEntity
import org.meshtastic.core.datastore.LocalStatsDataSource
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.model.util.NodeIdLookup
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.new_node_seen
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import org.meshtastic.proto.NodeInfo as ProtoNodeInfo
import org.meshtastic.proto.Position as ProtoPosition

/**
 * Maintains live mesh node state and exposes reactive node data for the app layer.
 */
@Single(binds = [NodeRepository::class, NodeIdLookup::class])
@Suppress("TooManyFunctions", "LongParameterList")
class SdkNodeRepositoryImpl(
    private val localStatsDataSource: LocalStatsDataSource,
    private val dbManager: DatabaseProvider,
    private val notificationManager: NotificationManager,
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

    // ── NodeRepository read surface ─────────────────────────────────────────

    override val nodeDBbyNum: StateFlow<Map<Int, Node>> = _nodeDBbyNum

    override val myNodeInfo: StateFlow<MyNodeInfo?> = _myNodeInfo

    override val ourNodeInfo: StateFlow<Node?> =
        combine(_nodeDBbyNum, _myNodeNum) { db, myNum -> myNum?.let { db[it] } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    override val myId: StateFlow<String?> =
        ourNodeInfo.map { it?.user?.id }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    override val localStats: StateFlow<LocalStats> =
        localStatsDataSource.localStatsFlow
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), LocalStats())

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
            ?: Node(num = runCatching { DataPacket.parseNodeNum(userId) }.getOrDefault(0), user = getUser(userId))

    override fun getUser(nodeNum: Int): User = getUser(DataPacket.nodeNumToId(nodeNum))

    private val last4 = 4

    override fun getUser(userId: String): User {
        val found = _nodeDBbyNum.value.values.find { it.user.id == userId }?.user
        if (found != null && found.long_name.isNotBlank() && found.short_name.isNotBlank()) {
            return found
        }
        val fallbackId = userId.takeLast(last4)
        val defaultLong =
            if (userId == DataPacket.nodeNumToId(DataPacket.LOCAL)) {
                ourNodeInfo.value?.user?.long_name?.takeIf { it.isNotBlank() } ?: "Local"
            } else {
                "Meshtastic $fallbackId"
            }
        val defaultShort =
            if (userId == DataPacket.nodeNumToId(DataPacket.LOCAL)) {
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

    // ── NodeRepository write surface ────────────────────────────────────────

    override suspend fun upsert(node: Node) {
        writeNode(node)
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

    // ── Runtime node state management ────────────────────────────────────────

    override val nodeDBbyNodeNum: Map<Int, Node>
        get() = _nodeDBbyNum.value

    override val nodeDBbyID: Map<String, Node>
        get() = _nodeDBbyNum.value.values.associateBy { it.user.id }

    override val isNodeDbReady = MutableStateFlow(false)

    override fun setNodeDbReady(ready: Boolean) {
        isNodeDbReady.value = ready
    }

    override val myNodeNum: StateFlow<Int?>
        get() = _myNodeNum

    override fun setMyNodeNum(num: Int?) {
        _myNodeNum.value = num
    }

    override val firmwareEdition = MutableStateFlow<FirmwareEdition?>(null)

    override fun setFirmwareEdition(edition: FirmwareEdition?) {
        firmwareEdition.value = edition
    }

    override fun clear() {
        _nodeDBbyNum.value = emptyMap()
        isNodeDbReady.value = false
        _myNodeNum.value = null
        firmwareEdition.value = null
    }

    override fun getMyNodeInfo(): MyNodeInfo? {
        val mi = _myNodeInfo.value ?: return null
        val myNode = _nodeDBbyNum.value[mi.myNodeNum]
        return MyNodeInfo(
            myNodeNum = mi.myNodeNum,
            hasGPS = (myNode?.position?.latitude_i ?: 0) != 0,
            model = mi.model ?: myNode?.user?.hw_model?.name,
            firmwareVersion = mi.firmwareVersion,
            couldUpdate = mi.couldUpdate,
            shouldUpdate = mi.shouldUpdate,
            currentPacketId = mi.currentPacketId,
            messageTimeoutMsec = mi.messageTimeoutMsec,
            minAppVersion = mi.minAppVersion,
            maxChannels = mi.maxChannels,
            hasWifi = mi.hasWifi,
            channelUtilization = 0f,
            airUtilTx = 0f,
            deviceId = mi.deviceId ?: myNode?.user?.id,
        )
    }

    override fun getMyId(): String {
        val num = _myNodeNum.value ?: _myNodeInfo.value?.myNodeNum ?: return ""
        return _nodeDBbyNum.value[num]?.user?.id ?: ""
    }

    override fun removeByNodenum(nodeNum: Int) {
        _nodeDBbyNum.update { it - nodeNum }
    }

    override fun updateNode(nodeNum: Int, withBroadcast: Boolean, channel: Int, transform: (Node) -> Node) {
        _nodeDBbyNum.update { map ->
            val current = map[nodeNum] ?: getOrCreateNode(nodeNum, channel)
            val transformed = transform(current)
            val enriched = enrichWithMetadata(transformed)
            map + (nodeNum to enriched)
        }
    }

    override fun handleReceivedUser(fromNum: Int, p: User, channel: Int, manuallyVerified: Boolean) {
        updateNode(fromNum, channel = channel) { node ->
            val newNode = (node.isUnknownUser && p.hw_model != HardwareModel.UNSET)
            val shouldPreserve = shouldPreserveExistingUser(node.user, p)

            val next =
                if (shouldPreserve) {
                    node.copy(channel = channel, manuallyVerified = manuallyVerified)
                } else {
                    val keyMatch = !node.hasPKC || node.user.public_key == p.public_key
                    val newUser = if (keyMatch) p else p.copy(public_key = ByteString.EMPTY)
                    node.copy(
                        user = newUser,
                        publicKey = newUser.public_key,
                        channel = channel,
                        manuallyVerified = manuallyVerified,
                    )
                }
            if (newNode && !shouldPreserve) {
                scope.handledLaunch {
                    notificationManager.dispatch(
                        Notification(
                            title = getStringSuspend(Res.string.new_node_seen, next.user.short_name),
                            message = next.user.long_name,
                            category = Notification.Category.NodeEvent,
                        ),
                    )
                }
            }
            next
        }
    }

    override fun handleReceivedPosition(fromNum: Int, myNodeNum: Int, p: ProtoPosition, defaultTime: Long) {
        val isZeroPos = (p.latitude_i ?: 0) == 0 && (p.longitude_i ?: 0) == 0
        @Suppress("ComplexCondition")
        if (myNodeNum == fromNum && isZeroPos && p.sats_in_view == 0 && p.time == 0) {
            Logger.d { "Ignoring empty position update for the local node" }
            return
        }

        updateNode(fromNum) { node ->
            val posTime = if (p.time != 0) p.time else (defaultTime / TIME_MS_TO_S).toInt()
            val newLastHeard = maxOf(node.lastHeard, posTime)

            val newPos =
                if (isZeroPos) {
                    p.copy(
                        time = posTime,
                        latitude_i = node.position.latitude_i,
                        longitude_i = node.position.longitude_i,
                        altitude = p.altitude ?: node.position.altitude,
                        sats_in_view = p.sats_in_view,
                    )
                } else {
                    p.copy(time = posTime)
                }

            node.copy(position = newPos, lastHeard = newLastHeard)
        }
    }

    override fun handleReceivedTelemetry(fromNum: Int, telemetry: Telemetry) {
        updateNode(fromNum) { node ->
            var nextNode = node
            telemetry.device_metrics?.let { nextNode = nextNode.copy(deviceMetrics = it) }
            telemetry.environment_metrics?.let { nextNode = nextNode.copy(environmentMetrics = it) }
            telemetry.power_metrics?.let { nextNode = nextNode.copy(powerMetrics = it) }
            val telemetryTime = if (telemetry.time != 0) telemetry.time else node.lastHeard
            val newLastHeard = maxOf(node.lastHeard, telemetryTime)
            nextNode.copy(lastHeard = newLastHeard)
        }
    }

    override fun installNodeInfo(info: ProtoNodeInfo, withBroadcast: Boolean) {
        updateNode(info.num) { node ->
            var next = node
            val user = info.user
            if (user != null) {
                if (shouldPreserveExistingUser(node.user, user)) {
                    // keep existing names
                } else {
                    var newUser =
                        user.let { if (it.is_licensed == true) it.copy(public_key = ByteString.EMPTY) else it }
                    if (info.via_mqtt && !newUser.long_name.endsWith(" (MQTT)")) {
                        newUser = newUser.copy(long_name = "${newUser.long_name} (MQTT)")
                    }
                    next = next.copy(user = newUser, publicKey = newUser.public_key)
                }
            }
            val position = info.position
            if (position != null) {
                next = next.copy(position = position)
            }
            next.copy(
                lastHeard = info.last_heard,
                deviceMetrics = info.device_metrics ?: next.deviceMetrics,
                channel = info.channel,
                viaMqtt = info.via_mqtt,
                hopsAway = info.hops_away ?: -1,
                isFavorite = info.is_favorite,
                isIgnored = info.is_ignored,
                isMuted = info.is_muted,
            )
        }
    }

    // ── NodeIdLookup ────────────────────────────────────────────────────────

    override fun toNodeID(nodeNum: Int): String = if (nodeNum == DataPacket.BROADCAST) {
        DataPacket.nodeNumToId(DataPacket.BROADCAST)
    } else {
        _nodeDBbyNum.value[nodeNum]?.user?.id ?: DataPacket.nodeNumToDefaultId(nodeNum)
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private companion object {
        private const val TIME_MS_TO_S = 1000L
    }

    private fun getOrCreateNode(n: Int, channel: Int = 0): Node = _nodeDBbyNum.value[n]
        ?: run {
            val userId = DataPacket.nodeNumToDefaultId(n)
            val defaultUser = User(
                id = userId,
                long_name = "Meshtastic ${userId.takeLast(n = 4)}",
                short_name = userId.takeLast(n = 4),
                hw_model = HardwareModel.UNSET,
            )
            Node(num = n, user = defaultUser, channel = channel)
        }

    /** Enriches a node with persisted local metadata (favorites, notes, ignore, mute). */
    private fun enrichWithMetadata(node: Node): Node {
        val meta = _metadataCache.value[node.num] ?: return node
        return node.copy(
            isFavorite = meta.isFavorite,
            isIgnored = meta.isIgnored,
            isMuted = meta.isMuted,
            notes = meta.notes,
            manuallyVerified = meta.manuallyVerified,
        )
    }

    /** Writes a node directly to the map with metadata enrichment. */
    private fun writeNode(node: Node) {
        val enriched = enrichWithMetadata(node)
        _nodeDBbyNum.update { map -> map + (enriched.num to enriched) }
    }

    private fun shouldPreserveExistingUser(existing: User, incoming: User): Boolean {
        val isDefaultName = (incoming.long_name).matches(Regex("^Meshtastic [0-9a-fA-F]{4}$"))
        val isDefaultHwModel = incoming.hw_model == HardwareModel.UNSET
        val hasExistingUser = (existing.id).isNotEmpty() && existing.hw_model != HardwareModel.UNSET
        return hasExistingUser && isDefaultName && isDefaultHwModel
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
        NodeSortOption.DISTANCE -> compareBy { it.hopsAway }
        NodeSortOption.HOPS_AWAY -> compareBy { it.hopsAway }
        NodeSortOption.CHANNEL -> compareBy { it.channel }
        NodeSortOption.VIA_MQTT -> compareByDescending { it.viaMqtt }
        NodeSortOption.VIA_FAVORITE -> compareByDescending { it.isFavorite }
    }
}

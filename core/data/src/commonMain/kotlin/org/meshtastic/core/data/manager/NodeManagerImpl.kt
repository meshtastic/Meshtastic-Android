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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import okio.ByteString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.clampTimestampToNow
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.NodeIdLookup
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.new_node_seen
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.StatusMessage
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import org.meshtastic.proto.NodeInfo as ProtoNodeInfo
import org.meshtastic.proto.Position as ProtoPosition

/** Implementation of [NodeManager] that maintains an in-memory database of the mesh. */
@Suppress("LongParameterList", "TooManyFunctions", "CyclomaticComplexMethod")
@Single(binds = [NodeManager::class, NodeIdLookup::class])
class NodeManagerImpl(
    private val nodeRepository: NodeRepository,
    private val notificationManager: NotificationManager,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : NodeManager {

    // Two indices over the same node set: byNum is the canonical store (mesh-level identifier), byId is a secondary
    // O(1) lookup for the user-facing hex string. Both are held in a single atomic ref so updates are observed
    // consistently — concurrent readers never see an entry present in one index but not the other.
    private data class NodeIndex(
        val byNum: PersistentMap<Int, Node> = persistentMapOf(),
        val byId: PersistentMap<String, Node> = persistentMapOf(),
    ) {
        fun put(num: Int, node: Node): NodeIndex {
            val previous = byNum[num]
            var nextById = byId
            // If the user.id changed (e.g. firmware reassigned the hex id) drop the stale id entry.
            if (previous != null && previous.user.id.isNotEmpty() && previous.user.id != node.user.id) {
                nextById = nextById.removing(previous.user.id)
            }
            if (node.user.id.isNotEmpty()) {
                nextById = nextById.putting(node.user.id, node)
            }
            return NodeIndex(byNum = byNum.putting(num, node), byId = nextById)
        }

        fun remove(num: Int): NodeIndex {
            val previous = byNum[num] ?: return this
            return NodeIndex(
                byNum = byNum.removing(num),
                byId = if (previous.user.id.isNotEmpty()) byId.removing(previous.user.id) else byId,
            )
        }

        companion object {
            fun fromByNum(nodes: Map<Int, Node>): NodeIndex {
                var byNum = persistentMapOf<Int, Node>()
                var byId = persistentMapOf<String, Node>()
                for ((num, node) in nodes) {
                    byNum = byNum.putting(num, node)
                    if (node.user.id.isNotEmpty()) byId = byId.putting(node.user.id, node)
                }
                return NodeIndex(byNum, byId)
            }
        }
    }

    private val nodeIndex = atomic(NodeIndex())

    override val nodeDBbyNodeNum: Map<Int, Node>
        get() = nodeIndex.value.byNum

    override fun getNodeById(id: String): Node? = nodeIndex.value.byId[id]

    override val isNodeDbReady = MutableStateFlow(false)
    override val allowNodeDbWrites = MutableStateFlow(false)

    override fun setNodeDbReady(ready: Boolean) {
        isNodeDbReady.value = ready
    }

    override fun setAllowNodeDbWrites(allowed: Boolean) {
        allowNodeDbWrites.value = allowed
    }

    override val myNodeNum = MutableStateFlow<Int?>(null)

    override fun setMyNodeNum(num: Int?) {
        myNodeNum.value = num
    }

    override val myDeviceId = MutableStateFlow<String?>(null)

    override fun setMyDeviceId(id: String?) {
        myDeviceId.value = id
    }

    override val firmwareEdition = MutableStateFlow<FirmwareEdition?>(null)

    override fun setFirmwareEdition(edition: FirmwareEdition?) {
        firmwareEdition.value = edition
    }

    companion object {
        private const val TIME_MS_TO_S = 1000L
    }

    override fun loadCachedNodeDB() {
        scope.handledLaunch {
            val nodes = nodeRepository.nodeDBbyNum.first()
            nodeIndex.value = NodeIndex.fromByNum(nodes)
            if (myNodeNum.value == null) {
                myNodeNum.value = nodeRepository.myNodeInfo.value?.myNodeNum
            }
        }
    }

    override fun clear() {
        nodeIndex.value = NodeIndex()
        isNodeDbReady.value = false
        allowNodeDbWrites.value = false
        myNodeNum.value = null
        myDeviceId.value = null
        firmwareEdition.value = null
    }

    override fun getMyNodeInfo(): MyNodeInfo? {
        val mi = nodeRepository.myNodeInfo.value ?: return null
        val myNode = nodeIndex.value.byNum[mi.myNodeNum]
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
        val num = myNodeNum.value ?: nodeRepository.myNodeInfo.value?.myNodeNum ?: return ""
        return nodeIndex.value.byNum[num]?.user?.id ?: ""
    }

    override fun removeByNodenum(nodeNum: Int) {
        nodeIndex.update { it.remove(nodeNum) }
    }

    internal fun getOrCreateNode(n: Int, channel: Int = 0): Node = nodeIndex.value.byNum[n]
        ?: run {
            val userId = NodeAddress.numToDefaultId(n)
            val defaultUser =
                User(
                    id = userId,
                    long_name = "Meshtastic ${userId.takeLast(n = 4)}",
                    short_name = userId.takeLast(n = 4),
                    hw_model = HardwareModel.UNSET,
                )

            Node(num = n, user = defaultUser, channel = channel)
        }

    override fun updateNode(nodeNum: Int, channel: Int, transform: (Node) -> Node) {
        // Perform read + transform inside update{} to ensure atomicity.
        // Without this, concurrent calls for the same nodeNum could read the same snapshot
        // and the last writer would silently overwrite the other's changes.
        var next: Node? = null
        nodeIndex.update { index ->
            val current = index.byNum[nodeNum] ?: getOrCreateNode(nodeNum, channel)
            val transformed = transform(current)
            next = transformed
            index.put(nodeNum, transformed)
        }
        val result = next ?: return

        if (result.user.id.isNotEmpty() && isNodeDbReady.value) {
            scope.handledLaunch { nodeRepository.upsert(result) }
        }
    }

    override fun handleReceivedUser(fromNum: Int, p: User, channel: Int, manuallyVerified: Boolean) {
        updateNode(fromNum) { node ->
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
                            id = next.num,
                            // Path format must stay in sync with DEEP_LINK_BASE_URI + DeepLinkRouter
                            // in core/navigation (avoided as a Gradle dep here to keep core/data free
                            // of Compose Navigation libs).
                            deepLinkUri = "meshtastic://meshtastic/nodes/${next.num}",
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
            val rawPosTime = if (p.time != 0) p.time else (defaultTime / TIME_MS_TO_S).toInt()
            val posTime = clampTimestampToNow(rawPosTime)
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
            telemetry.air_quality_metrics?.let { nextNode = nextNode.copy(airQualityMetrics = it) }
            val telemetryTime = if (telemetry.time != 0) telemetry.time else node.lastHeard
            val newLastHeard = clampTimestampToNow(maxOf(node.lastHeard, telemetryTime))
            nextNode.copy(lastHeard = newLastHeard)
        }
    }

    override fun handleReceivedPaxcounter(fromNum: Int, p: Paxcount) {
        updateNode(fromNum) { it.copy(paxcounter = p) }
    }

    override fun handleReceivedNodeStatus(fromNum: Int, s: StatusMessage) {
        updateNodeStatus(fromNum, s.status)
    }

    override fun updateNodeStatus(nodeNum: Int, status: String?) {
        updateNode(nodeNum) { it.copy(nodeStatus = status?.takeIf { s -> s.isNotEmpty() }) }
    }

    override fun installNodeInfo(info: ProtoNodeInfo) {
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
                val clampedPos = position.copy(time = clampTimestampToNow(position.time))
                next = next.copy(position = clampedPos)
            }
            next =
                next.copy(
                    lastHeard = clampTimestampToNow(info.last_heard),
                    deviceMetrics = info.device_metrics ?: next.deviceMetrics,
                    channel = info.channel,
                    viaMqtt = info.via_mqtt,
                    hopsAway = info.hops_away ?: -1,
                    isFavorite = info.is_favorite,
                    isIgnored = info.is_ignored,
                    isMuted = info.is_muted,
                    signsPackets = info.has_xeddsa_signed,
                )
            next
        }
    }

    override fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata) {
        scope.handledLaunch { nodeRepository.insertMetadata(nodeNum, metadata) }
    }

    private fun shouldPreserveExistingUser(existing: User, incoming: User): Boolean {
        val isDefaultName = (incoming.long_name).matches(Regex("^Meshtastic [0-9a-fA-F]{4}$"))
        val isDefaultHwModel = incoming.hw_model == HardwareModel.UNSET
        val hasExistingUser = (existing.id).isNotEmpty() && existing.hw_model != HardwareModel.UNSET
        return hasExistingUser && isDefaultName && isDefaultHwModel
    }

    override fun toNodeID(nodeNum: Int): String = if (nodeNum == NodeAddress.NODENUM_BROADCAST) {
        NodeAddress.ID_BROADCAST
    } else {
        nodeIndex.value.byNum[nodeNum]?.user?.id ?: NodeAddress.numToDefaultId(nodeNum)
    }
}

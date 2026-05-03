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
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import okio.ByteString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.DeviceMetrics
import org.meshtastic.core.model.EnvironmentMetrics
import org.meshtastic.core.model.MeshUser
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.util.NodeIdLookup
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.ServiceBroadcasts
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
    private val serviceBroadcasts: ServiceBroadcasts,
    private val notificationManager: NotificationManager,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : NodeManager {

    private val _nodeDBbyNodeNum = atomic(persistentMapOf<Int, Node>())
    private val _nodeDBbyID = atomic(persistentMapOf<String, Node>())

    override val nodeDBbyNodeNum: Map<Int, Node>
        get() = _nodeDBbyNodeNum.value

    override val nodeDBbyID: Map<String, Node>
        get() = _nodeDBbyID.value

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
            _nodeDBbyNodeNum.value = persistentMapOf<Int, Node>().putAll(nodes)
            val byId = mutableMapOf<String, Node>()
            nodes.values.forEach { byId[it.user.id] = it }
            _nodeDBbyID.value = persistentMapOf<String, Node>().putAll(byId)
            if (myNodeNum.value == null) {
                myNodeNum.value = nodeRepository.myNodeInfo.value?.myNodeNum
            }
        }
    }

    override fun clear() {
        _nodeDBbyNodeNum.value = persistentMapOf()
        _nodeDBbyID.value = persistentMapOf()
        isNodeDbReady.value = false
        allowNodeDbWrites.value = false
        myNodeNum.value = null
        firmwareEdition.value = null
    }

    override fun getMyNodeInfo(): MyNodeInfo? {
        val mi = nodeRepository.myNodeInfo.value ?: return null
        val myNode = _nodeDBbyNodeNum.value[mi.myNodeNum]
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
        return _nodeDBbyNodeNum.value[num]?.user?.id ?: ""
    }

    override fun getNodes(): List<NodeInfo> = _nodeDBbyNodeNum.value.values.map { it.toNodeInfo() }

    override fun removeByNodenum(nodeNum: Int) {
        val removed = atomic<Node?>(null)
        _nodeDBbyNodeNum.update { map ->
            val node = map[nodeNum]
            removed.value = node
            map.remove(nodeNum)
        }
        removed.value?.let { node -> _nodeDBbyID.update { it.remove(node.user.id) } }
    }

    internal fun getOrCreateNode(n: Int, channel: Int = 0): Node = _nodeDBbyNodeNum.value[n]
        ?: run {
            val userId = DataPacket.nodeNumToDefaultId(n)
            val defaultUser =
                User(
                    id = userId,
                    long_name = "Meshtastic ${userId.takeLast(n = 4)}",
                    short_name = userId.takeLast(n = 4),
                    hw_model = HardwareModel.UNSET,
                )

            Node(num = n, user = defaultUser, channel = channel)
        }

    override fun updateNode(nodeNum: Int, withBroadcast: Boolean, channel: Int, transform: (Node) -> Node) {
        // Perform read + transform inside update{} to ensure atomicity.
        // Without this, concurrent calls for the same nodeNum could read the same snapshot
        // and the last writer would silently overwrite the other's changes.
        var next: Node? = null
        _nodeDBbyNodeNum.update { map ->
            val current = map[nodeNum] ?: getOrCreateNode(nodeNum, channel)
            val transformed = transform(current)
            next = transformed
            map.put(nodeNum, transformed)
        }
        val result = next ?: return
        if (result.user.id.isNotEmpty()) {
            _nodeDBbyID.update { it.put(result.user.id, result) }
        }

        if (result.user.id.isNotEmpty() && isNodeDbReady.value) {
            scope.handledLaunch { nodeRepository.upsert(result) }
        }

        if (withBroadcast) {
            serviceBroadcasts.broadcastNodeChange(result)
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

    override fun handleReceivedPaxcounter(fromNum: Int, p: Paxcount) {
        updateNode(fromNum) { it.copy(paxcounter = p) }
    }

    override fun handleReceivedNodeStatus(fromNum: Int, s: StatusMessage) {
        updateNodeStatus(fromNum, s.status)
    }

    override fun updateNodeStatus(nodeNum: Int, status: String?) {
        updateNode(nodeNum) { it.copy(nodeStatus = status?.takeIf { s -> s.isNotEmpty() }) }
    }

    override fun installNodeInfo(info: ProtoNodeInfo, withBroadcast: Boolean) {
        updateNode(info.num, withBroadcast = withBroadcast) { node ->
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
            next =
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

    override fun toNodeID(nodeNum: Int): String = if (nodeNum == DataPacket.NODENUM_BROADCAST) {
        DataPacket.ID_BROADCAST
    } else {
        _nodeDBbyNodeNum.value[nodeNum]?.user?.id ?: DataPacket.nodeNumToDefaultId(nodeNum)
    }

    private fun Node.toNodeInfo(): NodeInfo = NodeInfo(
        num = num,
        user =
        MeshUser(
            id = user.id,
            longName = user.long_name,
            shortName = user.short_name,
            hwModel = user.hw_model,
            role = user.role.value,
        ),
        position =
        Position(
            latitude = latitude,
            longitude = longitude,
            altitude = position.altitude ?: 0,
            time = position.time,
            satellitesInView = position.sats_in_view,
            groundSpeed = position.ground_speed ?: 0,
            groundTrack = position.ground_track ?: 0,
            precisionBits = position.precision_bits,
        )
            .takeIf { latitude != 0.0 || longitude != 0.0 },
        snr = snr,
        rssi = rssi,
        lastHeard = lastHeard,
        deviceMetrics =
        DeviceMetrics(
            batteryLevel = deviceMetrics.battery_level ?: 0,
            voltage = deviceMetrics.voltage ?: 0f,
            channelUtilization = deviceMetrics.channel_utilization ?: 0f,
            airUtilTx = deviceMetrics.air_util_tx ?: 0f,
            uptimeSeconds = deviceMetrics.uptime_seconds ?: 0,
        ),
        channel = channel,
        environmentMetrics = EnvironmentMetrics.fromTelemetryProto(environmentMetrics, 0),
        hopsAway = hopsAway,
        nodeStatus = nodeStatus,
    )
}

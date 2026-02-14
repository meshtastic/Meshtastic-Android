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

import androidx.annotation.VisibleForTesting
import co.touchlab.kermit.Logger
import com.geeksville.mesh.concurrent.handledLaunch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import okio.ByteString
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.StatusMessage
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import org.meshtastic.proto.NodeInfo as ProtoNodeInfo
import org.meshtastic.proto.Position as ProtoPosition

@Suppress("LongParameterList", "TooManyFunctions", "CyclomaticComplexMethod")
@Singleton
class MeshNodeManager
@Inject
constructor(
    private val nodeRepository: NodeRepository?,
    private val serviceBroadcasts: MeshServiceBroadcasts?,
    private val serviceNotifications: MeshServiceNotifications?,
) {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val nodeDBbyNodeNum = ConcurrentHashMap<Int, NodeEntity>()
    val nodeDBbyID = ConcurrentHashMap<String, NodeEntity>()

    fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    val isNodeDbReady = MutableStateFlow(false)
    val allowNodeDbWrites = MutableStateFlow(false)

    var myNodeNum: Int? = null

    companion object {
        private const val TIME_MS_TO_S = 1000L
    }

    @VisibleForTesting internal constructor() : this(null, null, null)

    fun loadCachedNodeDB() {
        scope.handledLaunch {
            val nodes = nodeRepository?.getNodeDBbyNum()?.first() ?: emptyMap()
            nodeDBbyNodeNum.putAll(nodes)
            nodes.values.forEach { nodeDBbyID[it.user.id] = it }
            myNodeNum = nodeRepository?.myNodeInfo?.value?.myNodeNum
        }
    }

    fun clear() {
        nodeDBbyNodeNum.clear()
        nodeDBbyID.clear()
        isNodeDbReady.value = false
        allowNodeDbWrites.value = false
        myNodeNum = null
    }

    fun getMyNodeInfo(): MyNodeInfo? {
        val mi = nodeRepository?.myNodeInfo?.value ?: return null
        val myNode = nodeDBbyNodeNum[mi.myNodeNum]
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

    fun getMyId(): String {
        val num = myNodeNum ?: nodeRepository?.myNodeInfo?.value?.myNodeNum ?: return ""
        return nodeDBbyNodeNum[num]?.user?.id ?: ""
    }

    fun getNodes(): List<NodeInfo> = nodeDBbyNodeNum.values.map { it.toNodeInfo() }

    fun removeByNodenum(nodeNum: Int) {
        nodeDBbyNodeNum.remove(nodeNum)?.let { nodeDBbyID.remove(it.user.id) }
    }

    fun getOrCreateNodeInfo(n: Int, channel: Int = 0): NodeEntity = nodeDBbyNodeNum.getOrPut(n) {
        val userId = DataPacket.nodeNumToDefaultId(n)
        val defaultUser =
            User(
                id = userId,
                long_name = "Meshtastic ${userId.takeLast(n = 4)}",
                short_name = userId.takeLast(n = 4),
                hw_model = HardwareModel.UNSET,
            )

        NodeEntity(
            num = n,
            user = defaultUser,
            longName = defaultUser.long_name,
            shortName = defaultUser.short_name,
            channel = channel,
        )
    }

    fun updateNodeInfo(nodeNum: Int, withBroadcast: Boolean = true, channel: Int = 0, updateFn: (NodeEntity) -> Unit) {
        val info = getOrCreateNodeInfo(nodeNum, channel)
        updateFn(info)
        if (info.user.id.isNotEmpty()) {
            nodeDBbyID[info.user.id] = info
        }

        if (info.user.id.isNotEmpty() && isNodeDbReady.value) {
            scope.handledLaunch { nodeRepository?.upsert(info) }
        }

        if (withBroadcast) {
            serviceBroadcasts?.broadcastNodeChange(info.toNodeInfo())
        }
    }

    fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata) {
        scope.handledLaunch { nodeRepository?.insertMetadata(MetadataEntity(nodeNum, metadata)) }
    }

    fun handleReceivedUser(fromNum: Int, p: User, channel: Int = 0, manuallyVerified: Boolean = false) {
        updateNodeInfo(fromNum) {
            val newNode = (it.isUnknownUser && p.hw_model != HardwareModel.UNSET)
            val shouldPreserve = shouldPreserveExistingUser(it.user, p)

            if (shouldPreserve) {
                it.longName = it.user.long_name
                it.shortName = it.user.short_name
                it.channel = channel
                it.manuallyVerified = manuallyVerified
            } else {
                val keyMatch = !it.hasPKC || it.user.public_key == p.public_key
                it.user = if (keyMatch) p else p.copy(public_key = ByteString.EMPTY)
                it.longName = p.long_name
                it.shortName = p.short_name
                it.channel = channel
                it.manuallyVerified = manuallyVerified
                if (newNode) {
                    serviceNotifications?.showNewNodeSeenNotification(it)
                }
            }
        }
    }

    fun handleReceivedPosition(fromNum: Int, myNodeNum: Int, p: ProtoPosition, defaultTime: Long = nowMillis) {
        if (myNodeNum == fromNum && (p.latitude_i ?: 0) == 0 && (p.longitude_i ?: 0) == 0) {
            Logger.d { "Ignoring nop position update for the local node" }
        } else {
            updateNodeInfo(fromNum) { it.setPosition(p, (defaultTime / TIME_MS_TO_S).toInt()) }
        }
    }

    fun handleReceivedTelemetry(fromNum: Int, telemetry: Telemetry) {
        updateNodeInfo(fromNum) { nodeEntity ->
            when {
                telemetry.device_metrics != null -> nodeEntity.deviceTelemetry = telemetry
                telemetry.environment_metrics != null -> nodeEntity.environmentTelemetry = telemetry
                telemetry.power_metrics != null -> nodeEntity.powerTelemetry = telemetry
            }
        }
    }

    fun handleReceivedPaxcounter(fromNum: Int, p: Paxcount) {
        updateNodeInfo(fromNum) { it.paxcounter = p }
    }

    fun handleReceivedNodeStatus(fromNum: Int, s: StatusMessage) {
        updateNodeStatus(fromNum, s.status)
    }

    fun updateNodeStatus(nodeNum: Int, status: String) {
        updateNodeInfo(nodeNum) { it.nodeStatus = status }
    }

    fun installNodeInfo(info: ProtoNodeInfo, withBroadcast: Boolean = true) {
        updateNodeInfo(info.num, withBroadcast = withBroadcast) { entity ->
            val user = info.user
            if (user != null) {
                if (shouldPreserveExistingUser(entity.user, user)) {
                    entity.longName = entity.user.long_name
                    entity.shortName = entity.user.short_name
                } else {
                    var newUser = user.let { if (it.is_licensed) it.copy(public_key = ByteString.EMPTY) else it }
                    if (info.via_mqtt) {
                        newUser = newUser.copy(long_name = "${newUser.long_name} (MQTT)")
                    }
                    entity.user = newUser
                    entity.longName = newUser.long_name
                    entity.shortName = newUser.short_name
                }
            }
            val position = info.position
            if (position != null) {
                entity.position = position
                entity.latitude = Position.degD(position.latitude_i ?: 0)
                entity.longitude = Position.degD(position.longitude_i ?: 0)
            }
            entity.lastHeard = info.last_heard
            if (info.device_metrics != null) {
                entity.deviceTelemetry = Telemetry(device_metrics = info.device_metrics)
            }
            entity.channel = info.channel
            entity.viaMqtt = info.via_mqtt
            entity.hopsAway = info.hops_away ?: -1
            entity.isFavorite = info.is_favorite
            entity.isIgnored = info.is_ignored
            entity.isMuted = info.is_muted
        }
    }

    private fun shouldPreserveExistingUser(existing: User, incoming: User): Boolean {
        val isDefaultName = (incoming.long_name).matches(Regex("^Meshtastic [0-9a-fA-F]{4}$"))
        val isDefaultHwModel = incoming.hw_model == HardwareModel.UNSET
        val hasExistingUser = (existing.id).isNotEmpty() && existing.hw_model != HardwareModel.UNSET
        return hasExistingUser && isDefaultName && isDefaultHwModel
    }

    fun toNodeID(n: Int): String = if (n == DataPacket.NODENUM_BROADCAST) {
        DataPacket.ID_BROADCAST
    } else {
        nodeDBbyNodeNum[n]?.user?.id ?: DataPacket.nodeNumToDefaultId(n)
    }
}

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
package org.meshtastic.core.data.radio

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.model.AdminException
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.DataRequester
import org.meshtastic.core.model.DeviceAdmin
import org.meshtastic.core.model.DeviceAdminEdit
import org.meshtastic.core.model.DeviceControl
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.model.MessageSender
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.RemoteAdmin
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ChannelIndex
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioClient

/**
 * Shared KMP [RadioController] implementation that delegates all operations through the meshtastic-sdk.
 *
 * Feature modules inject [RadioController] and get SDK-backed behavior without needing platform-specific code.
 *
 * **Command dispatch:** All admin, telemetry, and routing operations go through [RadioClient.admin],
 * [RadioClient.telemetry], and [RadioClient.routing] respectively.
 *
 * **State distribution:** Handled by [SdkStateBridge], which feeds SDK flows into
 * [ServiceRepository] and [org.meshtastic.core.repository.NodeRepository].
 */
@Single(
    binds = [
        RadioController::class,
        MessageSender::class,
        DeviceAdmin::class,
        RemoteAdmin::class,
        DeviceControl::class,
        DataRequester::class,
    ],
)
@Suppress("TooManyFunctions", "LongParameterList")
class SdkRadioController(
    private val accessor: RadioClientAccessor,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val locationManager: MeshLocationManager,
    private val deliveryTracker: MessageDeliveryTracker,
    private val radioPrefs: RadioPrefs,
) : RadioController {

    private val packetIdCounter = atomic(1)

    private val client: RadioClient?
        get() = accessor.client.value

    private fun requireClient(): RadioClient {
        return client ?: run {
            Logger.w { "SdkRadioController: no active RadioClient" }
            throw IllegalStateException("RadioClient not connected")
        }
    }

    // ── Observable state ────────────────────────────────────────────────────

    override val connectionState: StateFlow<ConnectionState>
        get() = serviceRepository.connectionState

    override val clientNotification: StateFlow<ClientNotification?>
        get() = serviceRepository.clientNotification

    override fun clearClientNotification() {
        serviceRepository.clearClientNotification()
    }

    // ── Messaging ───────────────────────────────────────────────────────────

    override suspend fun sendMessage(packet: DataPacket) {
        val c = client ?: run {
            Logger.w { "sendMessage: no client, dropping packet" }
            return
        }
        val packetId = packet.id.takeIf { it != 0 } ?: getPacketId()
        try {
            val handle = c.send(
                portnum = PortNum.fromValue(packet.dataType) ?: PortNum.UNKNOWN_APP,
                payload = packet.bytes?.toByteArray() ?: byteArrayOf(),
                to = NodeId(packet.to),
                channel = ChannelIndex(packet.channel),
                wantAck = packet.wantAck,
                hopLimit = packet.hopLimit.takeIf { it > 0 },
            )
            deliveryTracker.track(packetId, handle)
            serviceRepository.emitMeshActivity(MeshActivity.Send)
        } catch (e: Exception) {
            Logger.e(e) { "sendMessage failed" }
            throw e
        }
    }

    // ── Node operations ─────────────────────────────────────────────────────

    override suspend fun favoriteNode(nodeNum: Int) {
        val c = requireClient()
        val node = nodeRepository.getNode(DataPacket.nodeNumToDefaultId(nodeNum))
        val currentlyFavorite = node.isFavorite
        c.admin.setFavorite(NodeId(nodeNum), !currentlyFavorite)
    }

    override suspend fun sendSharedContact(nodeNum: Int): Boolean {
        val c = client ?: return false
        val node = nodeRepository.getNode(DataPacket.nodeNumToDefaultId(nodeNum))
        val contact = SharedContact(
            node_num = node.num,
            user = node.user,
            manually_verified = node.manuallyVerified,
        )
        return when (c.admin.addContact(contact)) {
            is AdminResult.Success -> true
            else -> false
        }
    }

    // ── Local config ────────────────────────────────────────────────────────

    override suspend fun setLocalConfig(config: Config) {
        val c = requireClient()
        c.admin.setConfig(config)
    }

    override suspend fun setLocalChannel(channel: Channel) {
        val c = requireClient()
        c.admin.setChannel(channel)
    }

    // ── Remote admin (config/owner/channel) ─────────────────────────────────

    override suspend fun setOwner(destNum: Int, user: User) {
        val c = requireClient()
        c.admin.forNode(NodeId(destNum)).setOwner(user).unwrap()
    }

    override suspend fun setConfig(destNum: Int, config: Config) {
        val c = requireClient()
        c.admin.forNode(NodeId(destNum)).setConfig(config).unwrap()
    }

    override suspend fun setModuleConfig(destNum: Int, config: ModuleConfig) {
        val c = requireClient()
        c.admin.forNode(NodeId(destNum)).setModuleConfig(config).unwrap()
    }

    override suspend fun setRemoteChannel(destNum: Int, channel: Channel) {
        val c = requireClient()
        c.admin.forNode(NodeId(destNum)).setChannel(channel).unwrap()
    }

    override suspend fun setFixedPosition(destNum: Int, position: Position) {
        val c = requireClient()
        val protoPos = org.meshtastic.proto.Position(
            latitude_i = Position.degI(position.latitude),
            longitude_i = Position.degI(position.longitude),
            altitude = position.altitude,
            time = position.time,
        )
        c.admin.forNode(NodeId(destNum)).setFixedPosition(protoPos)
    }

    override suspend fun setRingtone(destNum: Int, ringtone: String) {
        val c = requireClient()
        c.admin.forNode(NodeId(destNum)).setRingtone(ringtone)
    }

    override suspend fun setCannedMessages(destNum: Int, messages: String) {
        val c = requireClient()
        c.admin.forNode(NodeId(destNum)).setCannedMessages(messages)
    }

    // ── Remote admin (getters) ──────────────────────────────────────────────

    override suspend fun getOwner(destNum: Int): User {
        val c = requireClient()
        return c.admin.forNode(NodeId(destNum)).getOwner().unwrap()
    }

    override suspend fun getConfig(destNum: Int, configType: Int): Config {
        val c = requireClient()
        val type = AdminMessage.ConfigType.fromValue(configType)
            ?: throw IllegalArgumentException("Unknown config type: $configType")
        return c.admin.forNode(NodeId(destNum)).getConfig(type).unwrap()
    }

    override suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int): ModuleConfig {
        val c = requireClient()
        val type = AdminMessage.ModuleConfigType.fromValue(moduleConfigType)
            ?: throw IllegalArgumentException("Unknown module config type: $moduleConfigType")
        return c.admin.forNode(NodeId(destNum)).getModuleConfig(type).unwrap()
    }

    override suspend fun getChannel(destNum: Int, index: Int): Channel {
        val c = requireClient()
        return c.admin.forNode(NodeId(destNum)).getChannel(ChannelIndex(index)).unwrap()
    }

    override suspend fun listChannels(destNum: Int): List<Channel> {
        val c = requireClient()
        return c.admin.forNode(NodeId(destNum)).listChannels().unwrap()
    }

    override suspend fun getRingtone(destNum: Int): String {
        val c = requireClient()
        return c.admin.forNode(NodeId(destNum)).getRingtone().unwrap()
    }

    override suspend fun getCannedMessages(destNum: Int): String {
        val c = requireClient()
        return c.admin.forNode(NodeId(destNum)).getCannedMessages().unwrap()
    }

    override suspend fun getDeviceConnectionStatus(destNum: Int): DeviceConnectionStatus {
        val c = requireClient()
        return c.admin.forNode(NodeId(destNum)).getDeviceConnectionStatus().unwrap()
    }

    // ── Lifecycle commands ───────────────────────────────────────────────────

    override suspend fun reboot(destNum: Int) {
        val c = requireClient()
        c.admin.forNode(NodeId(destNum)).reboot().unwrap()
    }

    override suspend fun rebootToDfu(nodeNum: Int) {
        val c = requireClient()
        c.admin.forNode(NodeId(nodeNum)).enterDfuMode().unwrap()
    }

    override suspend fun requestRebootOta(destNum: Int, mode: Int, hash: ByteArray?) {
        val c = requireClient()
        c.admin.forNode(NodeId(destNum)).rebootOta().unwrap()
    }

    override suspend fun shutdown(destNum: Int) {
        val c = requireClient()
        c.admin.forNode(NodeId(destNum)).shutdown().unwrap()
    }

    override suspend fun factoryReset(destNum: Int) {
        val c = requireClient()
        c.admin.forNode(NodeId(destNum)).factoryReset().unwrap()
    }

    override suspend fun nodedbReset(destNum: Int, preserveFavorites: Boolean) {
        val c = requireClient()
        // Note: The firmware's nodedb_reset command always preserves favorites (proto3 boolean
        // semantics — only true can be encoded). The preserveFavorites parameter only affects
        // local Room DB cleanup in AdminActionsUseCase.
        c.admin.forNode(NodeId(destNum)).nodeDbReset().unwrap()
    }

    override suspend fun removeByNodenum(nodeNum: Int) {
        val c = requireClient()
        c.admin.removeNode(NodeId(nodeNum)).unwrap()
    }

    // ── Data requests ───────────────────────────────────────────────────────

    override suspend fun requestPosition(destNum: Int, currentPosition: Position) {
        val c = client ?: return
        val posBytes = org.meshtastic.proto.Position(
            latitude_i = Position.degI(currentPosition.latitude),
            longitude_i = Position.degI(currentPosition.longitude),
            altitude = currentPosition.altitude,
            time = currentPosition.time,
        ).encode()
        c.send(
            portnum = PortNum.POSITION_APP,
            payload = posBytes,
            to = NodeId(destNum),
            wantAck = true,
        )
    }

    override suspend fun requestUserInfo(destNum: Int) {
        val c = client ?: return
        c.requestNodeInfo(NodeId(destNum))
    }

    override suspend fun requestTraceroute(destNum: Int) {
        val c = requireClient()
        c.routing.traceRoute(NodeId(destNum))
    }

    override suspend fun requestTelemetry(destNum: Int, type: TelemetryType) {
        val c = requireClient()
        val node = NodeId(destNum)
        when (type) {
            TelemetryType.DEVICE -> c.telemetry.requestDevice(node)
            TelemetryType.ENVIRONMENT -> c.telemetry.requestEnvironment(node)
            TelemetryType.AIR_QUALITY -> c.telemetry.requestAirQuality(node)
            TelemetryType.POWER -> c.telemetry.requestPower(node)
            TelemetryType.LOCAL_STATS -> c.telemetry.requestLocalStats()
            TelemetryType.HEALTH -> c.telemetry.requestHealth(node)
            TelemetryType.HOST -> c.telemetry.requestHost(node)
            TelemetryType.TRAFFIC_MANAGEMENT -> c.telemetry.requestTrafficManagement(node)
        }
    }

    override suspend fun requestNeighborInfo(destNum: Int) {
        val c = requireClient()
        c.routing.requestNeighborInfo(NodeId(destNum))
    }

    override suspend fun requestStoreForwardHistory(since: Int?, serverNodeNum: Int?): Boolean {
        val c = requireClient()
        val server = serverNodeNum?.let(::NodeId)
        return when (val result = c.storeForward.requestHistory(since = since, server = server)) {
            is AdminResult.Success -> {
                Logger.i {
                    "Requested S&F history since=${since ?: 0} server=${serverNodeNum ?: "auto"} pending=${result.value}"
                }
                true
            }
            else -> {
                Logger.w {
                    "S&F history request failed since=${since ?: 0} server=${serverNodeNum ?: "auto"} result=$result"
                }
                false
            }
        }
    }

    // ── Edit settings (transactional) ───────────────────────────────────────

    override suspend fun editSettings(destNum: Int, block: suspend DeviceAdminEdit.() -> Unit) {
        val c = requireClient()
        val admin = c.admin.forNode(NodeId(destNum))
        admin.editSettings {
            val edit = this
            val bridge = object : DeviceAdminEdit {
                override suspend fun setConfig(config: Config) { edit.setConfig(config) }
                override suspend fun setModuleConfig(config: ModuleConfig) { edit.setModuleConfig(config) }
                override suspend fun setOwner(user: User) { edit.setOwner(user) }
                override suspend fun setChannel(channel: Channel) { edit.setChannel(channel) }
            }
            block(bridge)
        }.unwrap()
    }

    // ── Utility ─────────────────────────────────────────────────────────────

    private fun getPacketId(): Int = packetIdCounter.getAndIncrement()

    override fun startProvideLocation() {
        // Location provision is managed at the app level; no-op here
    }

    override fun stopProvideLocation() {
        locationManager.stop()
    }

    override fun setDeviceAddress(address: String) {
        radioPrefs.setDevAddr(address)
        accessor.rebuildAndConnectAsync()
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /** Unwrap an [AdminResult], returning the value on success or throwing [AdminException] on failure. */
    private fun <T> AdminResult<T>.unwrap(): T = when (this) {
        is AdminResult.Success -> value
        is AdminResult.Timeout -> throw AdminException.Timeout()
        is AdminResult.Unauthorized -> throw AdminException.Unauthorized()
        is AdminResult.NodeUnreachable -> throw AdminException.NodeUnreachable()
        is AdminResult.SessionKeyExpired -> throw AdminException.SessionKeyExpired()
        is AdminResult.Failed -> throw AdminException.RoutingError(routingError.name)
    }
}

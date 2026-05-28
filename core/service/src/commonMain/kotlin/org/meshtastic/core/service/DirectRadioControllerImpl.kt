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
package org.meshtastic.core.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.Reaction
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.OTAMode
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User

/**
 * Platform-agnostic [RadioController] implementation modeled after the SDK's `AdminApiImpl` pattern.
 *
 * This class is the single composition root for all radio commands. It builds [AdminMessage] protos directly and
 * delegates to [CommandSender] for packet construction and transport — no intermediate handler layer, no ByteArray
 * encode/decode boundaries. Business logic (optimistic persistence, node state updates, analytics) lives here.
 *
 * This is the correct implementation for any target where the service runs in-process (Desktop, iOS, or Android in
 * single-process mode).
 */
@Suppress("TooManyFunctions", "LongParameterList")
class DirectRadioControllerImpl(
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val commandSender: CommandSender,
    private val nodeManager: NodeManager,
    private val radioInterfaceService: RadioInterfaceService,
    private val locationManager: MeshLocationManager,
    private val packetRepository: Lazy<PacketRepository>,
    private val dataHandler: Lazy<MeshDataHandler>,
    private val analytics: PlatformAnalytics,
    private val meshPrefs: MeshPrefs,
    private val uiPrefs: UiPrefs,
    private val databaseManager: DatabaseManager,
    private val notificationManager: NotificationManager,
    private val messageProcessor: Lazy<MeshMessageProcessor>,
    private val radioConfigRepository: RadioConfigRepository,
    private val scope: CoroutineScope,
    private val onDeviceAddressChanged: (() -> Unit)? = null,
) : RadioController {

    companion object {
        private const val DEFAULT_REBOOT_DELAY = 5
        private const val EMOJI_INDICATOR = 1
    }

    private val myNodeNum: Int
        get() = nodeManager.myNodeNum.value ?: 0

    // ── Connection State ────────────────────────────────────────────────────

    override val connectionState: StateFlow<ConnectionState>
        get() = serviceRepository.connectionState

    override val clientNotification: StateFlow<ClientNotification?>
        get() = serviceRepository.clientNotification

    override fun clearClientNotification() {
        serviceRepository.clearClientNotification()
    }

    // ── Messaging ───────────────────────────────────────────────────────────

    override suspend fun sendMessage(packet: DataPacket) {
        commandSender.sendData(packet)
        dataHandler.value.rememberDataPacket(packet, myNodeNum, false)
        val bytes = packet.bytes ?: ByteString.EMPTY
        analytics.track("data_send", DataPair("num_bytes", bytes.size), DataPair("type", packet.dataType))
    }

    override suspend fun sendReaction(emoji: String, replyId: Int, contactKey: String) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val parsedKey = ContactKey(contactKey)
        val channel = parsedKey.channel
        val destId = parsedKey.addressString
        val dataPacket =
            DataPacket(
                to = destId,
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                bytes = emoji.encodeToByteArray().toByteString(),
                channel = channel,
                replyId = replyId,
                wantAck = true,
                emoji = EMOJI_INDICATOR,
            )
                .apply { from = nodeManager.getMyId().takeIf { it.isNotEmpty() } ?: NodeAddress.ID_LOCAL }
        commandSender.sendData(dataPacket)
        val user = nodeManager.nodeDBbyNodeNum[myNum]?.user ?: User(id = nodeManager.getMyId())
        packetRepository.value.insertReaction(
            Reaction(
                replyId = replyId,
                user = user,
                emoji = emoji,
                timestamp = nowMillis,
                snr = 0f,
                rssi = 0,
                hopsAway = 0,
                packetId = dataPacket.id,
                status = MessageStatus.QUEUED,
                to = destId,
                channel = channel,
            ),
            myNum,
        )
    }

    // ── Node Management ─────────────────────────────────────────────────────

    override suspend fun favoriteNode(nodeNum: Int) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val node = nodeManager.nodeDBbyNodeNum[nodeNum] ?: return
        commandSender.sendAdmin(myNum) {
            if (node.isFavorite) {
                AdminMessage(remove_favorite_node = node.num)
            } else {
                AdminMessage(set_favorite_node = node.num)
            }
        }
        nodeManager.updateNode(node.num) { it.copy(isFavorite = !node.isFavorite) }
    }

    override suspend fun ignoreNode(nodeNum: Int) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val node = nodeManager.nodeDBbyNodeNum[nodeNum] ?: return
        val newIgnored = !node.isIgnored
        commandSender.sendAdmin(myNum) {
            if (newIgnored) AdminMessage(set_ignored_node = node.num) else AdminMessage(remove_ignored_node = node.num)
        }
        nodeManager.updateNode(node.num) { it.copy(isIgnored = newIgnored) }
        scope.handledLaunch { packetRepository.value.updateFilteredBySender(node.user.id, newIgnored) }
    }

    override suspend fun muteNode(nodeNum: Int) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val node = nodeManager.nodeDBbyNodeNum[nodeNum] ?: return
        commandSender.sendAdmin(myNum) { AdminMessage(toggle_muted_node = node.num) }
        nodeManager.updateNode(node.num) { it.copy(isMuted = !node.isMuted) }
    }

    override suspend fun removeByNodenum(packetId: Int, nodeNum: Int) {
        nodeManager.removeByNodenum(nodeNum)
        val myNum = nodeManager.myNodeNum.value ?: return
        commandSender.sendAdmin(myNum, packetId) { AdminMessage(remove_by_nodenum = nodeNum) }
    }

    // ── Contacts ────────────────────────────────────────────────────────────

    override suspend fun sendSharedContact(nodeNum: Int): Boolean {
        val myNum = nodeManager.myNodeNum.value ?: return false
        val nodeDef = nodeRepository.getNode(NodeAddress.numToDefaultId(nodeNum))
        val contact =
            SharedContact(node_num = nodeDef.num, user = nodeDef.user, manually_verified = nodeDef.manuallyVerified)
        return safeCatching { commandSender.sendAdminAwait(myNum) { AdminMessage(add_contact = contact) } }
            .getOrDefault(false)
    }

    override suspend fun importContact(contact: SharedContact) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val user = contact.user
        if (contact.node_num == 0 || user == null) {
            Logger.w { "importContact rejected: missing node_num or user (node_num=${contact.node_num})" }
            return
        }
        commandSender.sendAdmin(myNum) { AdminMessage(add_contact = contact) }
        nodeManager.handleReceivedUser(contact.node_num, user, manuallyVerified = contact.manually_verified)
    }

    // ── Device Metadata ─────────────────────────────────────────────────────

    override suspend fun refreshMetadata(destNum: Int) {
        commandSender.sendAdmin(destNum, wantResponse = true) { AdminMessage(get_device_metadata_request = true) }
    }

    // ── Owner ───────────────────────────────────────────────────────────────

    override suspend fun setOwner(destNum: Int, user: User, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(set_owner = user) }
        nodeManager.handleReceivedUser(destNum, user)
    }

    override suspend fun getOwner(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) { AdminMessage(get_owner_request = true) }
    }

    // ── Configuration ───────────────────────────────────────────────────────
    // Config and channel writes use fire-and-forget persistence (handledLaunch) intentionally.
    // The device is the source of truth — it re-sends its full config on every connection.
    // Local persistence is a cache optimization, not a correctness requirement.

    override suspend fun setLocalConfig(config: Config) {
        commandSender.sendAdmin(myNodeNum) { AdminMessage(set_config = config) }
        scope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
    }

    override suspend fun setConfig(destNum: Int, config: Config, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(set_config = config) }
        if (destNum == nodeManager.myNodeNum.value) {
            scope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
        }
    }

    override suspend fun getConfig(destNum: Int, configType: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            if (configType == AdminMessage.ConfigType.SESSIONKEY_CONFIG.value) {
                AdminMessage(get_device_metadata_request = true)
            } else {
                AdminMessage(get_config_request = AdminMessage.ConfigType.fromValue(configType))
            }
        }
    }

    override suspend fun setModuleConfig(destNum: Int, config: ModuleConfig, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(set_module_config = config) }
        if (destNum == nodeManager.myNodeNum.value) {
            config.statusmessage?.let { sm -> nodeManager.updateNodeStatus(destNum, sm.node_status) }
            scope.handledLaunch { radioConfigRepository.setLocalModuleConfig(config) }
        }
    }

    override suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage(get_module_config_request = AdminMessage.ModuleConfigType.fromValue(moduleConfigType))
        }
    }

    // ── Channels ────────────────────────────────────────────────────────────

    override suspend fun setLocalChannel(channel: Channel) {
        commandSender.sendAdmin(myNodeNum) { AdminMessage(set_channel = channel) }
        scope.handledLaunch { radioConfigRepository.updateChannelSettings(channel) }
    }

    override suspend fun setRemoteChannel(destNum: Int, channel: Channel, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(set_channel = channel) }
        if (destNum == nodeManager.myNodeNum.value) {
            scope.handledLaunch { radioConfigRepository.updateChannelSettings(channel) }
        }
    }

    override suspend fun getChannel(destNum: Int, index: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage(get_channel_request = index + 1)
        }
    }

    // ── Ringtone & Canned Messages ─────────────────────────────────────────

    override suspend fun setRingtone(destNum: Int, ringtone: String) {
        commandSender.sendAdmin(destNum) { AdminMessage(set_ringtone_message = ringtone) }
    }

    override suspend fun getRingtone(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) { AdminMessage(get_ringtone_request = true) }
    }

    override suspend fun setCannedMessages(destNum: Int, messages: String) {
        commandSender.sendAdmin(destNum) { AdminMessage(set_canned_message_module_messages = messages) }
    }

    override suspend fun getCannedMessages(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage(get_canned_message_module_messages_request = true)
        }
    }

    // ── Position ────────────────────────────────────────────────────────────

    override suspend fun setFixedPosition(destNum: Int, position: Position) {
        commandSender.setFixedPosition(destNum, position)
    }

    override suspend fun requestPosition(destNum: Int, currentPosition: Position) {
        if (destNum == nodeManager.myNodeNum.value) return
        val provideLocation = uiPrefs.shouldProvideNodeLocation(myNodeNum).value
        // Position(0.0, 0.0, 0) is the protocol-level "no position" sentinel.
        val resolvedPosition =
            if (provideLocation) {
                currentPosition.takeIf { it.isValid() }
                    ?: nodeManager.nodeDBbyNodeNum[myNodeNum]?.position?.let { Position(it) }?.takeIf { it.isValid() }
                    ?: Position(0.0, 0.0, 0)
            } else {
                Position(0.0, 0.0, 0)
            }
        commandSender.requestPosition(destNum, resolvedPosition)
    }

    // ── Device Status & Lifecycle ───────────────────────────────────────────

    override suspend fun getDeviceConnectionStatus(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId, wantResponse = true) {
            AdminMessage(get_device_connection_status_request = true)
        }
    }

    override suspend fun reboot(destNum: Int, packetId: Int) {
        Logger.i { "Reboot requested for node $destNum" }
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(reboot_seconds = DEFAULT_REBOOT_DELAY) }
    }

    override suspend fun rebootToDfu(nodeNum: Int) {
        commandSender.sendAdmin(nodeNum) { AdminMessage(enter_dfu_mode_request = true) }
    }

    override suspend fun requestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?) {
        val otaMode = OTAMode.fromValue(mode) ?: OTAMode.NO_REBOOT_OTA
        val otaEvent =
            AdminMessage.OTAEvent(reboot_ota_mode = otaMode, ota_hash = hash?.toByteString() ?: ByteString.EMPTY)
        commandSender.sendAdmin(destNum, requestId) { AdminMessage(ota_request = otaEvent) }
    }

    override suspend fun shutdown(destNum: Int, packetId: Int) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(shutdown_seconds = DEFAULT_REBOOT_DELAY) }
    }

    override suspend fun factoryReset(destNum: Int, packetId: Int) {
        Logger.i { "Factory reset requested for node $destNum" }
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(factory_reset_device = 1) }
    }

    override suspend fun nodedbReset(destNum: Int, packetId: Int, preserveFavorites: Boolean) {
        commandSender.sendAdmin(destNum, packetId) { AdminMessage(nodedb_reset = preserveFavorites) }
    }

    // ── Edit Settings (transactional) ───────────────────────────────────────

    override suspend fun beginEditSettings(destNum: Int) {
        commandSender.sendAdmin(destNum) { AdminMessage(begin_edit_settings = true) }
    }

    override suspend fun commitEditSettings(destNum: Int) {
        commandSender.sendAdmin(destNum) { AdminMessage(commit_edit_settings = true) }
    }

    // ── Telemetry & Discovery ───────────────────────────────────────────────

    override suspend fun requestUserInfo(destNum: Int) {
        if (destNum != nodeManager.myNodeNum.value) {
            commandSender.requestUserInfo(destNum)
        }
    }

    override suspend fun requestTraceroute(requestId: Int, destNum: Int) {
        commandSender.requestTraceroute(requestId, destNum)
    }

    override suspend fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) {
        commandSender.requestTelemetry(requestId, destNum, typeValue)
    }

    override suspend fun requestNeighborInfo(requestId: Int, destNum: Int) {
        commandSender.requestNeighborInfo(requestId, destNum)
    }

    // ── Packet ID & Location ────────────────────────────────────────────────

    override fun getPacketId(): Int = commandSender.generatePacketId()

    override fun startProvideLocation() {
        locationManager.restart()
    }

    override fun stopProvideLocation() {
        locationManager.stop()
    }

    // ── Device Address ──────────────────────────────────────────────────────

    override suspend fun setDeviceAddress(address: String) {
        switchDevice(address)
        radioInterfaceService.setDeviceAddress(address)
        onDeviceAddressChanged?.invoke()
    }

    private suspend fun switchDevice(deviceAddr: String) {
        val currentAddr = meshPrefs.deviceAddress.value
        if (deviceAddr != currentAddr) {
            Logger.i { "Device address changed, switching database and clearing node DB" }
            meshPrefs.setDeviceAddress(deviceAddr)
            nodeManager.clear()
            messageProcessor.value.clearEarlyPackets()
            databaseManager.switchActiveDatabase(deviceAddr)
            notificationManager.cancelAll()
            nodeManager.loadCachedNodeDB()
        }
    }
}

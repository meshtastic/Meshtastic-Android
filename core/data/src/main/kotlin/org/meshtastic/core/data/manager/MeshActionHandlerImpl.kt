/*
 * Copyright (c) 2025 Meshtastic LLC
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

import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.analytics.DataPair
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.ignoreException
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshUser
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.Reaction
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.prefs.mesh.MeshPrefs
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.DatabaseManager
import org.meshtastic.core.repository.MeshActionHandler
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.OTAMode
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("LongParameterList", "TooManyFunctions", "CyclomaticComplexMethod")
@Singleton
class MeshActionHandlerImpl @Inject constructor(
    private val nodeManager: NodeManager,
    private val commandSender: CommandSender,
    private val packetRepository: Lazy<PacketRepository>,
    private val serviceBroadcasts: ServiceBroadcasts,
    private val dataHandler: Lazy<MeshDataHandler>,
    private val analytics: PlatformAnalytics,
    private val meshPrefs: MeshPrefs,
    private val databaseManager: DatabaseManager,
    private val serviceNotifications: MeshServiceNotifications,
    private val messageProcessor: Lazy<MeshMessageProcessor>,
) : MeshActionHandler {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    companion object {
        private const val DEFAULT_REBOOT_DELAY = 5
        private const val EMOJI_INDICATOR = 1
    }

    override fun onServiceAction(action: ServiceAction) {
        ignoreException {
            val myNodeNum = nodeManager.myNodeNum ?: return@ignoreException
            when (action) {
                is ServiceAction.Favorite -> handleFavorite(action, myNodeNum)
                is ServiceAction.Ignore -> handleIgnore(action, myNodeNum)
                is ServiceAction.Mute -> handleMute(action, myNodeNum)
                is ServiceAction.Reaction -> handleReaction(action, myNodeNum)
                is ServiceAction.ImportContact -> handleImportContact(action, myNodeNum)
                is ServiceAction.SendContact -> {
                    commandSender.sendAdmin(myNodeNum) { AdminMessage(add_contact = action.contact) }
                }
                is ServiceAction.GetDeviceMetadata -> {
                    commandSender.sendAdmin(action.destNum, wantResponse = true) {
                        AdminMessage(get_device_metadata_request = true)
                    }
                }
            }
        }
    }

    private fun handleFavorite(action: ServiceAction.Favorite, myNodeNum: Int) {
        val node = action.node
        commandSender.sendAdmin(myNodeNum) {
            if (node.isFavorite) {
                AdminMessage(remove_favorite_node = node.num)
            } else {
                AdminMessage(set_favorite_node = node.num)
            }
        }
        nodeManager.updateNode(node.num) { it.copy(isFavorite = !node.isFavorite) }
    }

    private fun handleIgnore(action: ServiceAction.Ignore, myNodeNum: Int) {
        val node = action.node
        val newIgnoredStatus = !node.isIgnored
        commandSender.sendAdmin(myNodeNum) {
            if (newIgnoredStatus) {
                AdminMessage(set_ignored_node = node.num)
            } else {
                AdminMessage(remove_ignored_node = node.num)
            }
        }
        nodeManager.updateNode(node.num) { it.copy(isIgnored = newIgnoredStatus) }
        scope.handledLaunch { packetRepository.get().updateFilteredBySender(node.user.id, newIgnoredStatus) }
    }

    private fun handleMute(action: ServiceAction.Mute, myNodeNum: Int) {
        val node = action.node
        commandSender.sendAdmin(myNodeNum) { AdminMessage(toggle_muted_node = node.num) }
        nodeManager.updateNode(node.num) { it.copy(isMuted = !node.isMuted) }
    }

    private fun handleReaction(action: ServiceAction.Reaction, myNodeNum: Int) {
        val channel = action.contactKey[0].digitToInt()
        val destId = action.contactKey.substring(1)
        val dataPacket =
            DataPacket(
                to = destId,
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                bytes = action.emoji.encodeToByteArray().toByteString(),
                channel = channel,
                replyId = action.replyId,
                wantAck = true,
                emoji = EMOJI_INDICATOR,
            )
                .apply { from = nodeManager.getMyId().takeIf { it.isNotEmpty() } ?: DataPacket.ID_LOCAL }
        commandSender.sendData(dataPacket)
        rememberReaction(action, dataPacket.id, myNodeNum)
    }

    private fun handleImportContact(action: ServiceAction.ImportContact, myNodeNum: Int) {
        val verifiedContact = action.contact.copy(manually_verified = true)
        commandSender.sendAdmin(myNodeNum) { AdminMessage(add_contact = verifiedContact) }
        nodeManager.handleReceivedUser(
            verifiedContact.node_num,
            verifiedContact.user ?: User(),
            manuallyVerified = true,
        )
    }

    private fun rememberReaction(action: ServiceAction.Reaction, packetId: Int, myNodeNum: Int) {
        scope.handledLaunch {
            val user = nodeManager.nodeDBbyNodeNum[myNodeNum]?.user ?: User(id = nodeManager.getMyId())
            val reaction =
                Reaction(
                    replyId = action.replyId,
                    user = user,
                    emoji = action.emoji,
                    timestamp = nowMillis,
                    snr = 0f,
                    rssi = 0,
                    hopsAway = 0,
                    packetId = packetId,
                    status = MessageStatus.QUEUED,
                    to = action.contactKey.substring(1),
                    channel = action.contactKey[0].digitToInt(),
                )
            packetRepository.get().insertReaction(reaction, myNodeNum)
        }
    }

    override fun handleSetOwner(u: MeshUser, myNodeNum: Int) {
        val newUser = User(id = u.id, long_name = u.longName, short_name = u.shortName, is_licensed = u.isLicensed)
        commandSender.sendAdmin(myNodeNum) { AdminMessage(set_owner = newUser) }
        nodeManager.handleReceivedUser(myNodeNum, newUser)
    }

    override fun handleSend(p: DataPacket, myNodeNum: Int) {
        commandSender.sendData(p)
        serviceBroadcasts.broadcastMessageStatus(p.id, p.status ?: MessageStatus.UNKNOWN)
        dataHandler.get().rememberDataPacket(p, myNodeNum, false)
        val bytes = p.bytes ?: okio.ByteString.EMPTY
        analytics.track("data_send", DataPair("num_bytes", bytes.size), DataPair("type", p.dataType))
    }

    override fun handleRequestPosition(destNum: Int, position: Position, myNodeNum: Int) {
        if (destNum != myNodeNum) {
            val provideLocation = meshPrefs.shouldProvideNodeLocation(myNodeNum)
            val currentPosition =
                when {
                    provideLocation && position.isValid() -> position
                    else ->
                        nodeManager.nodeDBbyNodeNum[myNodeNum]?.position?.let { Position(it) }?.takeIf { it.isValid() }
                }
            currentPosition?.let { commandSender.requestPosition(destNum, it) }
        }
    }

    override fun handleRemoveByNodenum(nodeNum: Int, requestId: Int, myNodeNum: Int) {
        nodeManager.removeByNodenum(nodeNum)
        commandSender.sendAdmin(myNodeNum, requestId) { AdminMessage(remove_by_nodenum = nodeNum) }
    }

    override fun handleSetRemoteOwner(id: Int, destNum: Int, payload: ByteArray) {
        val u = User.ADAPTER.decode(payload)
        commandSender.sendAdmin(destNum, id) { AdminMessage(set_owner = u) }
        nodeManager.handleReceivedUser(destNum, u)
    }

    override fun handleGetRemoteOwner(id: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) { AdminMessage(get_owner_request = true) }
    }

    override fun handleSetConfig(payload: ByteArray, myNodeNum: Int) {
        val c = Config.ADAPTER.decode(payload)
        commandSender.sendAdmin(myNodeNum) { AdminMessage(set_config = c) }
    }

    override fun handleSetRemoteConfig(id: Int, destNum: Int, payload: ByteArray) {
        val c = Config.ADAPTER.decode(payload)
        commandSender.sendAdmin(destNum, id) { AdminMessage(set_config = c) }
    }

    override fun handleGetRemoteConfig(id: Int, destNum: Int, config: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) {
            if (config == AdminMessage.ConfigType.SESSIONKEY_CONFIG.value) {
                AdminMessage(get_device_metadata_request = true)
            } else {
                AdminMessage(get_config_request = AdminMessage.ConfigType.fromValue(config))
            }
        }
    }

    override fun handleSetModuleConfig(id: Int, destNum: Int, payload: ByteArray) {
        val c = ModuleConfig.ADAPTER.decode(payload)
        commandSender.sendAdmin(destNum, id) { AdminMessage(set_module_config = c) }
        c.statusmessage?.let { sm -> nodeManager.updateNodeStatus(destNum, sm.node_status) }
    }

    override fun handleGetModuleConfig(id: Int, destNum: Int, config: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) {
            AdminMessage(get_module_config_request = AdminMessage.ModuleConfigType.fromValue(config))
        }
    }

    override fun handleSetRingtone(destNum: Int, ringtone: String) {
        commandSender.sendAdmin(destNum) { AdminMessage(set_ringtone_message = ringtone) }
    }

    override fun handleGetRingtone(id: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) { AdminMessage(get_ringtone_request = true) }
    }

    override fun handleSetCannedMessages(destNum: Int, messages: String) {
        commandSender.sendAdmin(destNum) { AdminMessage(set_canned_message_module_messages = messages) }
    }

    override fun handleGetCannedMessages(id: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) {
            AdminMessage(get_canned_message_module_messages_request = true)
        }
    }

    override fun handleSetChannel(payload: ByteArray?, myNodeNum: Int) {
        if (payload != null) {
            val c = Channel.ADAPTER.decode(payload)
            commandSender.sendAdmin(myNodeNum) { AdminMessage(set_channel = c) }
        }
    }

    override fun handleSetRemoteChannel(id: Int, destNum: Int, payload: ByteArray?) {
        if (payload != null) {
            val c = Channel.ADAPTER.decode(payload)
            commandSender.sendAdmin(destNum, id) { AdminMessage(set_channel = c) }
        }
    }

    override fun handleGetRemoteChannel(id: Int, destNum: Int, index: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) { AdminMessage(get_channel_request = index + 1) }
    }

    override fun handleRequestNeighborInfo(requestId: Int, destNum: Int) {
        commandSender.requestNeighborInfo(requestId, destNum)
    }

    override fun handleBeginEditSettings(destNum: Int) {
        commandSender.sendAdmin(destNum) { AdminMessage(begin_edit_settings = true) }
    }

    override fun handleCommitEditSettings(destNum: Int) {
        commandSender.sendAdmin(destNum) { AdminMessage(commit_edit_settings = true) }
    }

    override fun handleRebootToDfu(destNum: Int) {
        commandSender.sendAdmin(destNum) { AdminMessage(enter_dfu_mode_request = true) }
    }

    override fun handleRequestTelemetry(requestId: Int, destNum: Int, type: Int) {
        commandSender.requestTelemetry(requestId, destNum, type)
    }

    override fun handleRequestShutdown(requestId: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, requestId) { AdminMessage(shutdown_seconds = DEFAULT_REBOOT_DELAY) }
    }

    override fun handleRequestReboot(requestId: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, requestId) { AdminMessage(reboot_seconds = DEFAULT_REBOOT_DELAY) }
    }

    override fun handleRequestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?) {
        val otaMode = OTAMode.fromValue(mode) ?: OTAMode.NO_REBOOT_OTA
        val otaEvent =
            AdminMessage.OTAEvent(reboot_ota_mode = otaMode, ota_hash = hash?.toByteString() ?: okio.ByteString.EMPTY)
        commandSender.sendAdmin(destNum, requestId) { AdminMessage(ota_request = otaEvent) }
    }

    override fun handleRequestFactoryReset(requestId: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, requestId) { AdminMessage(factory_reset_device = 1) }
    }

    override fun handleRequestNodedbReset(requestId: Int, destNum: Int, preserveFavorites: Boolean) {
        commandSender.sendAdmin(destNum, requestId) { AdminMessage(nodedb_reset = preserveFavorites) }
    }

    override fun handleGetDeviceConnectionStatus(requestId: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, requestId, wantResponse = true) {
            AdminMessage(get_device_connection_status_request = true)
        }
    }

    override fun handleUpdateLastAddress(deviceAddr: String?) {
        val currentAddr = meshPrefs.deviceAddress
        if (deviceAddr != currentAddr) {
            meshPrefs.deviceAddress = deviceAddr
            scope.handledLaunch {
                nodeManager.clear()
                messageProcessor.get().clearEarlyPackets()
                databaseManager.switchActiveDatabase(deviceAddr)
                serviceNotifications.clearNotifications()
                nodeManager.loadCachedNodeDB()
            }
        }
    }
}

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

import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.util.ignoreException
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.analytics.DataPair
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Position
import org.meshtastic.core.prefs.mesh.MeshPrefs
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.OTAMode
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("LongParameterList", "TooManyFunctions")
@Singleton
class MeshActionHandler
@Inject
constructor(
    private val nodeManager: MeshNodeManager,
    private val commandSender: MeshCommandSender,
    private val packetRepository: Lazy<PacketRepository>,
    private val serviceBroadcasts: MeshServiceBroadcasts,
    private val dataHandler: MeshDataHandler,
    private val analytics: PlatformAnalytics,
    private val meshPrefs: MeshPrefs,
    private val databaseManager: DatabaseManager,
    private val serviceNotifications: MeshServiceNotifications,
    private val messageProcessor: Lazy<MeshMessageProcessor>,
) {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    companion object {
        private const val DEFAULT_REBOOT_DELAY = 5
    }

    fun onServiceAction(action: ServiceAction) {
        ignoreException {
            val myNodeNum = nodeManager.myNodeNum ?: return@ignoreException
            when (action) {
                is ServiceAction.Favorite -> handleFavorite(action, myNodeNum)
                is ServiceAction.Ignore -> handleIgnore(action, myNodeNum)
                is ServiceAction.Mute -> handleMute(action, myNodeNum)
                is ServiceAction.Reaction -> handleReaction(action, myNodeNum)
                is ServiceAction.ImportContact -> handleImportContact(action, myNodeNum)
                is ServiceAction.SendContact -> {
                    commandSender.sendAdmin(myNodeNum) { copy(add_contact = action.contact) }
                }
                is ServiceAction.GetDeviceMetadata -> {
                    commandSender.sendAdmin(action.destNum, wantResponse = true) {
                        copy(get_device_metadata_request = true)
                    }
                }
            }
        }
    }

    private fun handleFavorite(action: ServiceAction.Favorite, myNodeNum: Int) {
        val node = action.node
        commandSender.sendAdmin(myNodeNum) {
            if (node.isFavorite) copy(remove_favorite_node = node.num) else copy(set_favorite_node = node.num)
        }
        nodeManager.updateNodeInfo(node.num) { it.isFavorite = !node.isFavorite }
    }

    private fun handleIgnore(action: ServiceAction.Ignore, myNodeNum: Int) {
        val node = action.node
        commandSender.sendAdmin(myNodeNum) {
            if (node.isIgnored) copy(remove_ignored_node = node.num) else copy(set_ignored_node = node.num)
        }
        nodeManager.updateNodeInfo(node.num) { it.isIgnored = !node.isIgnored }
    }

    private fun handleMute(action: ServiceAction.Mute, myNodeNum: Int) {
        val node = action.node
        commandSender.sendAdmin(myNodeNum) { copy(toggle_muted_node = node.num) }
        nodeManager.updateNodeInfo(node.num) { it.isMuted = !node.isMuted }
    }

    private fun handleReaction(action: ServiceAction.Reaction, myNodeNum: Int) {
        val channel = action.contactKey[0].digitToInt()
        val destId = action.contactKey.substring(1)
        val dataPacket =
            org.meshtastic.core.model
                .DataPacket(
                    to = destId,
                    dataType = PortNum.TEXT_MESSAGE_APP.value,
                    bytes = action.emoji.encodeToByteArray(),
                    channel = channel,
                    replyId = action.replyId,
                    wantAck = true,
                    emoji = action.emoji.codePointAt(0),
                )
                .apply { from = nodeManager.getMyId().takeIf { it.isNotEmpty() } ?: DataPacket.ID_LOCAL }
        commandSender.sendData(dataPacket)
        rememberReaction(action, dataPacket.id, myNodeNum)
    }

    private fun handleImportContact(action: ServiceAction.ImportContact, myNodeNum: Int) {
        val verifiedContact = action.contact.copy(manually_verified = true)
        commandSender.sendAdmin(myNodeNum) { copy(add_contact = verifiedContact) }
        val user = verifiedContact.user ?: User()
        nodeManager.handleReceivedUser(verifiedContact.node_num, user, manuallyVerified = true)
    }

    private fun rememberReaction(action: ServiceAction.Reaction, packetId: Int, myNodeNum: Int) {
        scope.handledLaunch {
            val reaction =
                ReactionEntity(
                    myNodeNum = myNodeNum,
                    replyId = action.replyId,
                    userId = nodeManager.getMyId().takeIf { it.isNotEmpty() } ?: DataPacket.ID_LOCAL,
                    emoji = action.emoji,
                    timestamp = System.currentTimeMillis(),
                    snr = 0f,
                    rssi = 0,
                    hopsAway = 0,
                    packetId = packetId,
                    status = MessageStatus.QUEUED,
                    to = action.contactKey.substring(1),
                    channel = action.contactKey[0].digitToInt(),
                )
            packetRepository.get().insertReaction(reaction)
        }
    }

    fun handleSetOwner(u: User, myNodeNum: Int) {
        commandSender.sendAdmin(myNodeNum) { copy(set_owner = u) }
        nodeManager.handleReceivedUser(myNodeNum, u)
    }

    fun handleSend(p: DataPacket, myNodeNum: Int) {
        commandSender.sendData(p)
        serviceBroadcasts.broadcastMessageStatus(p)
        dataHandler.rememberDataPacket(p, myNodeNum, false)
        val bytes = p.bytes ?: ByteArray(0)
        analytics.track("data_send", DataPair("num_bytes", bytes.size), DataPair("type", p.dataType))
    }

    fun handleRequestPosition(destNum: Int, position: Position, myNodeNum: Int) {
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

    fun handleRemoveByNodenum(nodeNum: Int, requestId: Int, myNodeNum: Int) {
        nodeManager.removeByNodenum(nodeNum)
        commandSender.sendAdmin(myNodeNum, requestId) { copy(remove_by_nodenum = nodeNum) }
    }

    fun handleSetRemoteOwner(id: Int, payload: ByteArray, myNodeNum: Int) {
        val u = User.ADAPTER.decode(payload)
        commandSender.sendAdmin(myNodeNum, id) { copy(set_owner = u) }
    }

    fun handleGetRemoteOwner(id: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) { copy(get_owner_request = true) }
    }

    fun handleSetConfig(payload: ByteArray, myNodeNum: Int) {
        val c = Config.ADAPTER.decode(payload)
        commandSender.sendAdmin(myNodeNum) { copy(set_config = c) }
    }

    fun handleSetRemoteConfig(id: Int, num: Int, payload: ByteArray) {
        val c = Config.ADAPTER.decode(payload)
        commandSender.sendAdmin(num, id) { copy(set_config = c) }
    }

    fun handleGetRemoteConfig(id: Int, destNum: Int, config: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) {
            if (config == AdminMessage.ConfigType.SESSIONKEY_CONFIG.value) {
                copy(get_device_metadata_request = true)
            } else {
                copy(get_config_request = AdminMessage.ConfigType.fromValue(config))
            }
        }
    }

    fun handleSetModuleConfig(id: Int, num: Int, payload: ByteArray) {
        val c = ModuleConfig.ADAPTER.decode(payload)
        commandSender.sendAdmin(num, id) { copy(set_module_config = c) }
    }

    fun handleGetModuleConfig(id: Int, destNum: Int, config: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) {
            copy(get_module_config_request = AdminMessage.ModuleConfigType.fromValue(config))
        }
    }

    fun handleSetRingtone(destNum: Int, ringtone: String) {
        commandSender.sendAdmin(destNum) { copy(set_ringtone_message = ringtone) }
    }

    fun handleGetRingtone(id: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) { copy(get_ringtone_request = true) }
    }

    fun handleSetCannedMessages(destNum: Int, messages: String) {
        commandSender.sendAdmin(destNum) { copy(set_canned_message_module_messages = messages) }
    }

    fun handleGetCannedMessages(id: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) {
            copy(get_canned_message_module_messages_request = true)
        }
    }

    fun handleSetChannel(payload: ByteArray?, myNodeNum: Int) {
        if (payload != null) {
            val c = org.meshtastic.proto.Channel.ADAPTER.decode(payload)
            commandSender.sendAdmin(myNodeNum) { copy(set_channel = c) }
        }
    }

    fun handleSetRemoteChannel(id: Int, num: Int, payload: ByteArray?) {
        if (payload != null) {
            val c = org.meshtastic.proto.Channel.ADAPTER.decode(payload)
            commandSender.sendAdmin(num, id) { copy(set_channel = c) }
        }
    }

    fun handleGetRemoteChannel(id: Int, destNum: Int, index: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) { copy(get_channel_request = index + 1) }
    }

    fun handleRequestNeighborInfo(requestId: Int, destNum: Int) {
        commandSender.requestNeighborInfo(requestId, destNum)
    }

    fun handleBeginEditSettings(myNodeNum: Int) {
        commandSender.sendAdmin(myNodeNum) { copy(begin_edit_settings = true) }
    }

    fun handleCommitEditSettings(myNodeNum: Int) {
        commandSender.sendAdmin(myNodeNum) { copy(commit_edit_settings = true) }
    }

    fun handleRebootToDfu(myNodeNum: Int) {
        commandSender.sendAdmin(myNodeNum) { copy(enter_dfu_mode_request = true) }
    }

    fun handleRequestTelemetry(requestId: Int, destNum: Int, type: Int) {
        commandSender.requestTelemetry(requestId, destNum, type)
    }

    fun handleRequestShutdown(requestId: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, requestId) { copy(shutdown_seconds = DEFAULT_REBOOT_DELAY) }
    }

    fun handleRequestReboot(requestId: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, requestId) { copy(reboot_seconds = DEFAULT_REBOOT_DELAY) }
    }

    fun handleRequestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?) {
        val otaMode = OTAMode.fromValue(mode) ?: OTAMode.NO_REBOOT_OTA
        val otaEvent =
            AdminMessage.OTAEvent(reboot_ota_mode = otaMode, ota_hash = hash?.toByteString() ?: okio.ByteString.EMPTY)
        commandSender.sendAdmin(destNum, requestId) { copy(ota_request = otaEvent) }
    }

    fun handleRequestFactoryReset(requestId: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, requestId) { copy(factory_reset_device = 1) }
    }

    fun handleRequestNodedbReset(requestId: Int, destNum: Int, preserveFavorites: Boolean) {
        commandSender.sendAdmin(destNum, requestId) { copy(nodedb_reset = preserveFavorites) }
    }

    fun handleGetDeviceConnectionStatus(requestId: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, requestId, wantResponse = true) {
            copy(get_device_connection_status_request = true)
        }
    }

    fun handleUpdateLastAddress(deviceAddr: String?) {
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

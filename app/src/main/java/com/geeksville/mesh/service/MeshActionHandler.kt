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
import org.meshtastic.core.analytics.DataPair
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Position
import org.meshtastic.core.prefs.mesh.MeshPrefs
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.proto.AdminProtos
import org.meshtastic.proto.ChannelProtos
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.ModuleConfigProtos
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.user
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
                is ServiceAction.Reaction -> handleReaction(action)
                is ServiceAction.ImportContact -> handleImportContact(action, myNodeNum)
                is ServiceAction.SendContact -> {
                    commandSender.sendAdmin(myNodeNum) { addContact = action.contact }
                }
                is ServiceAction.GetDeviceMetadata -> {
                    commandSender.sendAdmin(action.destNum, wantResponse = true) { getDeviceMetadataRequest = true }
                }
            }
        }
    }

    private fun handleFavorite(action: ServiceAction.Favorite, myNodeNum: Int) {
        val node = action.node
        commandSender.sendAdmin(myNodeNum) {
            if (node.isFavorite) removeFavoriteNode = node.num else setFavoriteNode = node.num
        }
        nodeManager.updateNodeInfo(node.num) { it.isFavorite = !node.isFavorite }
    }

    private fun handleIgnore(action: ServiceAction.Ignore, myNodeNum: Int) {
        val node = action.node
        commandSender.sendAdmin(myNodeNum) {
            if (node.isIgnored) removeIgnoredNode = node.num else setIgnoredNode = node.num
        }
        nodeManager.updateNodeInfo(node.num) { it.isIgnored = !node.isIgnored }
    }

    private fun handleReaction(action: ServiceAction.Reaction) {
        val channel = action.contactKey[0].digitToInt()
        val destId = action.contactKey.substring(1)
        val dataPacket =
            org.meshtastic.core.model.DataPacket(
                to = destId,
                dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                bytes = action.emoji.encodeToByteArray(),
                channel = channel,
                replyId = action.replyId,
                wantAck = false,
                emoji = action.emoji.codePointAt(0),
            )
        commandSender.sendData(dataPacket)
        rememberReaction(action)
    }

    private fun handleImportContact(action: ServiceAction.ImportContact, myNodeNum: Int) {
        val verifiedContact = action.contact.toBuilder().setManuallyVerified(true).build()
        commandSender.sendAdmin(myNodeNum) { addContact = verifiedContact }
        nodeManager.handleReceivedUser(verifiedContact.nodeNum, verifiedContact.user, manuallyVerified = true)
    }

    private fun rememberReaction(action: ServiceAction.Reaction) {
        scope.handledLaunch {
            val reaction =
                ReactionEntity(
                    replyId = action.replyId,
                    userId = DataPacket.ID_LOCAL,
                    emoji = action.emoji,
                    timestamp = System.currentTimeMillis(),
                    snr = 0f,
                    rssi = 0,
                    hopsAway = 0,
                )
            packetRepository.get().insertReaction(reaction)
        }
    }

    fun handleSetOwner(u: org.meshtastic.core.model.MeshUser, myNodeNum: Int) {
        commandSender.sendAdmin(myNodeNum) {
            setOwner = user {
                id = u.id
                longName = u.longName
                shortName = u.shortName
                isLicensed = u.isLicensed
            }
        }
        nodeManager.handleReceivedUser(
            myNodeNum,
            user {
                id = u.id
                longName = u.longName
                shortName = u.shortName
                isLicensed = u.isLicensed
            },
        )
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
        commandSender.sendAdmin(myNodeNum, requestId) { removeByNodenum = nodeNum }
    }

    fun handleSetRemoteOwner(id: Int, payload: ByteArray, myNodeNum: Int) {
        val u = MeshProtos.User.parseFrom(payload)
        commandSender.sendAdmin(myNodeNum, id) { setOwner = u }
    }

    fun handleGetRemoteOwner(id: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) { getOwnerRequest = true }
    }

    fun handleSetConfig(payload: ByteArray, myNodeNum: Int) {
        val c = ConfigProtos.Config.parseFrom(payload)
        commandSender.sendAdmin(myNodeNum) { setConfig = c }
    }

    fun handleSetRemoteConfig(id: Int, num: Int, payload: ByteArray) {
        val c = ConfigProtos.Config.parseFrom(payload)
        commandSender.sendAdmin(num, id) { setConfig = c }
    }

    fun handleGetRemoteConfig(id: Int, destNum: Int, config: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) {
            if (config == AdminProtos.AdminMessage.ConfigType.SESSIONKEY_CONFIG_VALUE) {
                getDeviceMetadataRequest = true
            } else {
                getConfigRequestValue = config
            }
        }
    }

    fun handleSetModuleConfig(id: Int, num: Int, payload: ByteArray) {
        val c = ModuleConfigProtos.ModuleConfig.parseFrom(payload)
        commandSender.sendAdmin(num, id) { setModuleConfig = c }
    }

    fun handleGetModuleConfig(id: Int, destNum: Int, config: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) { getModuleConfigRequestValue = config }
    }

    fun handleSetRingtone(destNum: Int, ringtone: String) {
        commandSender.sendAdmin(destNum) { setRingtoneMessage = ringtone }
    }

    fun handleGetRingtone(id: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) { getRingtoneRequest = true }
    }

    fun handleSetCannedMessages(destNum: Int, messages: String) {
        commandSender.sendAdmin(destNum) { setCannedMessageModuleMessages = messages }
    }

    fun handleGetCannedMessages(id: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) { getCannedMessageModuleMessagesRequest = true }
    }

    fun handleSetChannel(payload: ByteArray?, myNodeNum: Int) {
        if (payload != null) {
            val c = ChannelProtos.Channel.parseFrom(payload)
            commandSender.sendAdmin(myNodeNum) { setChannel = c }
        }
    }

    fun handleSetRemoteChannel(id: Int, num: Int, payload: ByteArray?) {
        if (payload != null) {
            val c = ChannelProtos.Channel.parseFrom(payload)
            commandSender.sendAdmin(num, id) { setChannel = c }
        }
    }

    fun handleGetRemoteChannel(id: Int, destNum: Int, index: Int) {
        commandSender.sendAdmin(destNum, id, wantResponse = true) { getChannelRequest = index + 1 }
    }

    fun handleRequestNeighborInfo(requestId: Int, destNum: Int) {
        commandSender.requestNeighborInfo(requestId, destNum)
    }

    fun handleBeginEditSettings(myNodeNum: Int) {
        commandSender.sendAdmin(myNodeNum) { beginEditSettings = true }
    }

    fun handleCommitEditSettings(myNodeNum: Int) {
        commandSender.sendAdmin(myNodeNum) { commitEditSettings = true }
    }

    fun handleRebootToDfu(myNodeNum: Int) {
        commandSender.sendAdmin(myNodeNum) { enterDfuModeRequest = true }
    }

    fun handleRequestTelemetry(requestId: Int, destNum: Int, type: Int) {
        commandSender.requestTelemetry(requestId, destNum, type)
    }

    fun handleRequestShutdown(requestId: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, requestId) { shutdownSeconds = DEFAULT_REBOOT_DELAY }
    }

    fun handleRequestReboot(requestId: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, requestId) { rebootSeconds = DEFAULT_REBOOT_DELAY }
    }

    fun handleRequestFactoryReset(requestId: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, requestId) { factoryResetDevice = 1 }
    }

    fun handleRequestNodedbReset(requestId: Int, destNum: Int, preserveFavorites: Boolean) {
        commandSender.sendAdmin(destNum, requestId) { nodedbReset = preserveFavorites }
    }

    fun handleGetDeviceConnectionStatus(requestId: Int, destNum: Int) {
        commandSender.sendAdmin(destNum, requestId, wantResponse = true) { getDeviceConnectionStatusRequest = true }
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

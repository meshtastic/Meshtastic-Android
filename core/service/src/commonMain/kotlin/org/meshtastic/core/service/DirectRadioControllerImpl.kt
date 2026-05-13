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

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User

/**
 * Platform-agnostic [RadioController] implementation that delegates directly to service-layer handlers.
 *
 * Unlike [AndroidRadioControllerImpl], which routes every call through the AIDL [IMeshService] binder, this
 * implementation talks directly to [CommandSender], [MeshRouter.actionHandler], [ServiceRepository], and [NodeManager].
 * This is the correct implementation for any target where the service runs in-process (Desktop, iOS, or Android in
 * single-process mode).
 *
 * This eliminates the need for [NoopRadioController] on non-Android targets.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class DirectRadioControllerImpl(
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val commandSender: CommandSender,
    private val router: MeshRouter,
    private val nodeManager: NodeManager,
    private val radioInterfaceService: RadioInterfaceService,
    private val locationManager: MeshLocationManager,
) : RadioController {

    private val actionHandler
        get() = router.actionHandler

    private val myNodeNum: Int
        get() = nodeManager.myNodeNum.value ?: 0

    /** Delegates to [ServiceRepository.connectionState] — the canonical app-level source of truth. */
    override val connectionState: StateFlow<ConnectionState>
        get() = serviceRepository.connectionState

    override val clientNotification: StateFlow<ClientNotification?>
        get() = serviceRepository.clientNotification

    override suspend fun sendMessage(packet: DataPacket) {
        actionHandler.handleSend(packet, myNodeNum)
    }

    override fun clearClientNotification() {
        serviceRepository.clearClientNotification()
    }

    override suspend fun favoriteNode(nodeNum: Int) {
        val nodeDef = nodeRepository.getNode(DataPacket.nodeNumToDefaultId(nodeNum))
        serviceRepository.onServiceAction(ServiceAction.Favorite(nodeDef))
    }

    override suspend fun sendSharedContact(nodeNum: Int): Boolean {
        val nodeDef = nodeRepository.getNode(DataPacket.nodeNumToDefaultId(nodeNum))
        val contact =
            SharedContact(node_num = nodeDef.num, user = nodeDef.user, manually_verified = nodeDef.manuallyVerified)
        val action = ServiceAction.SendContact(contact)
        serviceRepository.onServiceAction(action)
        return action.result.await()
    }

    override suspend fun setLocalConfig(config: Config) {
        actionHandler.handleSetConfig(config.encode(), myNodeNum)
    }

    override suspend fun setLocalChannel(channel: Channel) {
        actionHandler.handleSetChannel(channel.encode(), myNodeNum)
    }

    override suspend fun setOwner(destNum: Int, user: User, packetId: Int) {
        actionHandler.handleSetRemoteOwner(packetId, destNum, user.encode())
    }

    override suspend fun setConfig(destNum: Int, config: Config, packetId: Int) {
        actionHandler.handleSetRemoteConfig(packetId, destNum, config.encode())
    }

    override suspend fun setModuleConfig(destNum: Int, config: ModuleConfig, packetId: Int) {
        actionHandler.handleSetModuleConfig(packetId, destNum, config.encode())
    }

    override suspend fun setRemoteChannel(destNum: Int, channel: Channel, packetId: Int) {
        actionHandler.handleSetRemoteChannel(packetId, destNum, channel.encode())
    }

    override suspend fun setFixedPosition(destNum: Int, position: Position) {
        commandSender.setFixedPosition(destNum, position)
    }

    override suspend fun setRingtone(destNum: Int, ringtone: String) {
        actionHandler.handleSetRingtone(destNum, ringtone)
    }

    override suspend fun setCannedMessages(destNum: Int, messages: String) {
        actionHandler.handleSetCannedMessages(destNum, messages)
    }

    override suspend fun getOwner(destNum: Int, packetId: Int) {
        actionHandler.handleGetRemoteOwner(packetId, destNum)
    }

    override suspend fun getConfig(destNum: Int, configType: Int, packetId: Int) {
        actionHandler.handleGetRemoteConfig(packetId, destNum, configType)
    }

    override suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int, packetId: Int) {
        actionHandler.handleGetModuleConfig(packetId, destNum, moduleConfigType)
    }

    override suspend fun getChannel(destNum: Int, index: Int, packetId: Int) {
        actionHandler.handleGetRemoteChannel(packetId, destNum, index)
    }

    override suspend fun getRingtone(destNum: Int, packetId: Int) {
        actionHandler.handleGetRingtone(packetId, destNum)
    }

    override suspend fun getCannedMessages(destNum: Int, packetId: Int) {
        actionHandler.handleGetCannedMessages(packetId, destNum)
    }

    override suspend fun getDeviceConnectionStatus(destNum: Int, packetId: Int) {
        actionHandler.handleGetDeviceConnectionStatus(packetId, destNum)
    }

    override suspend fun reboot(destNum: Int, packetId: Int) {
        actionHandler.handleRequestReboot(packetId, destNum)
    }

    override suspend fun rebootToDfu(nodeNum: Int) {
        actionHandler.handleRebootToDfu(nodeNum)
    }

    override suspend fun requestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?) {
        actionHandler.handleRequestRebootOta(requestId, destNum, mode, hash)
    }

    override suspend fun shutdown(destNum: Int, packetId: Int) {
        actionHandler.handleRequestShutdown(packetId, destNum)
    }

    override suspend fun factoryReset(destNum: Int, packetId: Int) {
        actionHandler.handleRequestFactoryReset(packetId, destNum)
    }

    override suspend fun nodedbReset(destNum: Int, packetId: Int, preserveFavorites: Boolean) {
        actionHandler.handleRequestNodedbReset(packetId, destNum, preserveFavorites)
    }

    override suspend fun removeByNodenum(packetId: Int, nodeNum: Int) {
        val myNode = nodeManager.myNodeNum.value
        if (myNode != null) {
            actionHandler.handleRemoveByNodenum(nodeNum, packetId, myNode)
        } else {
            nodeManager.removeByNodenum(nodeNum)
        }
    }

    override suspend fun requestPosition(destNum: Int, currentPosition: Position) {
        actionHandler.handleRequestPosition(destNum, currentPosition, myNodeNum)
    }

    override suspend fun requestUserInfo(destNum: Int) {
        if (destNum != myNodeNum) {
            commandSender.requestUserInfo(destNum)
        }
    }

    override suspend fun requestTraceroute(requestId: Int, destNum: Int) {
        commandSender.requestTraceroute(requestId, destNum)
    }

    override suspend fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) {
        actionHandler.handleRequestTelemetry(requestId, destNum, typeValue)
    }

    override suspend fun requestNeighborInfo(requestId: Int, destNum: Int) {
        actionHandler.handleRequestNeighborInfo(requestId, destNum)
    }

    override suspend fun beginEditSettings(destNum: Int) {
        actionHandler.handleBeginEditSettings(destNum)
    }

    override suspend fun commitEditSettings(destNum: Int) {
        actionHandler.handleCommitEditSettings(destNum)
    }

    override fun getPacketId(): Int = commandSender.generatePacketId()

    override fun startProvideLocation() {
        // Location provision requires a scope — typically managed by the orchestrator.
        // On platforms without GPS hardware (desktop), this is a no-op via the injected locationManager.
    }

    override fun stopProvideLocation() {
        locationManager.stop()
    }

    override fun setDeviceAddress(address: String) {
        actionHandler.handleUpdateLastAddress(address)
        radioInterfaceService.setDeviceAddress(address)
    }
}

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

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.proto.ClientNotification

@Single
@Suppress("TooManyFunctions")
class AndroidRadioControllerImpl(
    private val context: Context,
    private val serviceRepository: AndroidServiceRepository,
    private val nodeRepository: NodeRepository,
) : RadioController {

    override val connectionState: StateFlow<ConnectionState>
        get() = serviceRepository.connectionState

    override val clientNotification: StateFlow<ClientNotification?>
        get() = serviceRepository.clientNotification

    override suspend fun sendMessage(packet: DataPacket) {
        // Bridging to the existing flow via IMeshService
        serviceRepository.meshService?.send(packet)
    }

    override fun clearClientNotification() {
        serviceRepository.clearClientNotification()
    }

    override suspend fun favoriteNode(nodeNum: Int) {
        val nodeDef = nodeRepository.getNode(nodeNum.toString())
        serviceRepository.onServiceAction(ServiceAction.Favorite(nodeDef))
    }

    override suspend fun sendSharedContact(nodeNum: Int) {
        val nodeDef = nodeRepository.getNode(nodeNum.toString())
        val contact =
            org.meshtastic.proto.SharedContact(
                node_num = nodeDef.num,
                user = nodeDef.user,
                manually_verified = nodeDef.manuallyVerified,
            )
        serviceRepository.onServiceAction(ServiceAction.SendContact(contact))
    }

    override suspend fun setLocalConfig(config: org.meshtastic.proto.Config) {
        serviceRepository.meshService?.setConfig(config.encode())
    }

    override suspend fun setLocalChannel(channel: org.meshtastic.proto.Channel) {
        serviceRepository.meshService?.setChannel(channel.encode())
    }

    override suspend fun setOwner(destNum: Int, user: org.meshtastic.proto.User, packetId: Int) {
        serviceRepository.meshService?.setRemoteOwner(packetId, destNum, user.encode())
    }

    override suspend fun setConfig(destNum: Int, config: org.meshtastic.proto.Config, packetId: Int) {
        serviceRepository.meshService?.setRemoteConfig(packetId, destNum, config.encode())
    }

    override suspend fun setModuleConfig(destNum: Int, config: org.meshtastic.proto.ModuleConfig, packetId: Int) {
        serviceRepository.meshService?.setModuleConfig(packetId, destNum, config.encode())
    }

    override suspend fun setRemoteChannel(destNum: Int, channel: org.meshtastic.proto.Channel, packetId: Int) {
        serviceRepository.meshService?.setRemoteChannel(packetId, destNum, channel.encode())
    }

    override suspend fun setFixedPosition(destNum: Int, position: org.meshtastic.core.model.Position) {
        serviceRepository.meshService?.setFixedPosition(destNum, position)
    }

    override suspend fun setRingtone(destNum: Int, ringtone: String) {
        serviceRepository.meshService?.setRingtone(destNum, ringtone)
    }

    override suspend fun setCannedMessages(destNum: Int, messages: String) {
        serviceRepository.meshService?.setCannedMessages(destNum, messages)
    }

    override suspend fun getOwner(destNum: Int, packetId: Int) {
        serviceRepository.meshService?.getRemoteOwner(packetId, destNum)
    }

    override suspend fun getConfig(destNum: Int, configType: Int, packetId: Int) {
        serviceRepository.meshService?.getRemoteConfig(packetId, destNum, configType)
    }

    override suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int, packetId: Int) {
        serviceRepository.meshService?.getModuleConfig(packetId, destNum, moduleConfigType)
    }

    override suspend fun getChannel(destNum: Int, index: Int, packetId: Int) {
        serviceRepository.meshService?.getRemoteChannel(packetId, destNum, index)
    }

    override suspend fun getRingtone(destNum: Int, packetId: Int) {
        serviceRepository.meshService?.getRingtone(packetId, destNum)
    }

    override suspend fun getCannedMessages(destNum: Int, packetId: Int) {
        serviceRepository.meshService?.getCannedMessages(packetId, destNum)
    }

    override suspend fun getDeviceConnectionStatus(destNum: Int, packetId: Int) {
        serviceRepository.meshService?.getDeviceConnectionStatus(packetId, destNum)
    }

    override suspend fun reboot(destNum: Int, packetId: Int) {
        serviceRepository.meshService?.requestReboot(packetId, destNum)
    }

    override suspend fun rebootToDfu(nodeNum: Int) {
        serviceRepository.meshService?.rebootToDfu(nodeNum)
    }

    override suspend fun requestRebootOta(requestId: Int, destNum: Int, mode: Int, hash: ByteArray?) {
        serviceRepository.meshService?.requestRebootOta(requestId, destNum, mode, hash)
    }

    override suspend fun shutdown(destNum: Int, packetId: Int) {
        serviceRepository.meshService?.requestShutdown(packetId, destNum)
    }

    override suspend fun factoryReset(destNum: Int, packetId: Int) {
        serviceRepository.meshService?.requestFactoryReset(packetId, destNum)
    }

    override suspend fun nodedbReset(destNum: Int, packetId: Int, preserveFavorites: Boolean) {
        serviceRepository.meshService?.requestNodedbReset(packetId, destNum, preserveFavorites)
    }

    override suspend fun removeByNodenum(packetId: Int, nodeNum: Int) {
        serviceRepository.meshService?.removeByNodenum(packetId, nodeNum)
    }

    override suspend fun requestPosition(destNum: Int, currentPosition: org.meshtastic.core.model.Position) {
        serviceRepository.meshService?.requestPosition(destNum, currentPosition)
    }

    override suspend fun requestUserInfo(destNum: Int) {
        serviceRepository.meshService?.requestUserInfo(destNum)
    }

    override suspend fun requestTraceroute(requestId: Int, destNum: Int) {
        serviceRepository.meshService?.requestTraceroute(requestId, destNum)
    }

    override suspend fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) {
        serviceRepository.meshService?.requestTelemetry(requestId, destNum, typeValue)
    }

    override suspend fun requestNeighborInfo(requestId: Int, destNum: Int) {
        serviceRepository.meshService?.requestNeighborInfo(requestId, destNum)
    }

    override suspend fun beginEditSettings(destNum: Int) {
        serviceRepository.meshService?.beginEditSettings(destNum)
    }

    override suspend fun commitEditSettings(destNum: Int) {
        serviceRepository.meshService?.commitEditSettings(destNum)
    }

    override fun getPacketId(): Int = serviceRepository.meshService?.getPacketId() ?: 0

    override fun startProvideLocation() {
        serviceRepository.meshService?.startProvideLocation()
    }

    override fun stopProvideLocation() {
        serviceRepository.meshService?.stopProvideLocation()
    }

    override fun setDeviceAddress(address: String) {
        @Suppress("DEPRECATION") // Internal use: routes address change through AIDL binder
        serviceRepository.meshService?.setDeviceAddress(address)
        // Ensure service is running/restarted to handle the new address
        val intent =
            android.content.Intent().apply {
                setClassName("com.geeksville.mesh", "org.meshtastic.core.service.MeshService")
            }
        context.startForegroundService(intent)
    }
}

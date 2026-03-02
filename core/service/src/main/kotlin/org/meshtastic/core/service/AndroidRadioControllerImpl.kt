package org.meshtastic.core.service

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.RadioController
import org.meshtastic.proto.ClientNotification
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidRadioControllerImpl @Inject constructor(
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository
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
        val contact = org.meshtastic.proto.SharedContact(
            node_num = nodeDef.num,
            user = nodeDef.user,
            manually_verified = nodeDef.manuallyVerified
        )
        serviceRepository.onServiceAction(ServiceAction.SendContact(contact))
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

    override suspend fun beginEditSettings(destNum: Int) {
        serviceRepository.meshService?.beginEditSettings(destNum)
    }

    override suspend fun commitEditSettings(destNum: Int) {
        serviceRepository.meshService?.commitEditSettings(destNum)
    }

    override fun getPacketId(): Int {
        return serviceRepository.meshService?.getPacketId() ?: 0
    }

    override fun startProvideLocation() {
        serviceRepository.meshService?.startProvideLocation()
    }

    override fun stopProvideLocation() {
        serviceRepository.meshService?.stopProvideLocation()
    }
}

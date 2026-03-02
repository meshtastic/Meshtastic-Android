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

    override suspend fun setFixedPosition(destNum: Int, position: org.meshtastic.core.model.Position) {
        serviceRepository.meshService?.setFixedPosition(destNum, position)
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

    override suspend fun beginEditSettings(destNum: Int) {
        serviceRepository.meshService?.beginEditSettings(destNum)
    }

    override suspend fun commitEditSettings(destNum: Int) {
        serviceRepository.meshService?.commitEditSettings(destNum)
    }

    override fun getPacketId(): Int {
        return serviceRepository.meshService?.getPacketId() ?: 0
    }
}

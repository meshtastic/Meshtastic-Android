package org.meshtastic.core.service

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.RadioController
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.SharedContact
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
        val contact = SharedContact(
            node_num = nodeDef.num,
            user = nodeDef.user,
            manually_verified = nodeDef.manuallyVerified
        )
        serviceRepository.onServiceAction(ServiceAction.SendContact(contact))
    }
}

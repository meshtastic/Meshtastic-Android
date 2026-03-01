package org.meshtastic.core.model

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.proto.ClientNotification

interface RadioController {
    val connectionState: StateFlow<ConnectionState>
    val clientNotification: StateFlow<ClientNotification?>

    suspend fun sendMessage(packet: DataPacket)
    fun clearClientNotification()

    // Abstracted ServiceActions
    suspend fun favoriteNode(nodeNum: Int)
    suspend fun sendSharedContact(nodeNum: Int)
}

package org.meshtastic.core.model

import kotlinx.coroutines.flow.StateFlow

interface RadioController {
    val connectionState: StateFlow<ConnectionState>
    
    // Using core models, entirely skipping Java protobufs
    suspend fun sendMessage(packet: DataPacket)

    // Abstracted ServiceActions
    suspend fun favoriteNode(nodeNum: Int)
    suspend fun sendSharedContact(nodeNum: Int)
}

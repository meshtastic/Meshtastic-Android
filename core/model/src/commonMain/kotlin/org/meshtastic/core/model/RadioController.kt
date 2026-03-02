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

    // Radio configuration
    suspend fun setOwner(destNum: Int, user: org.meshtastic.proto.User, packetId: Int)
    suspend fun setConfig(destNum: Int, config: org.meshtastic.proto.Config, packetId: Int)
    suspend fun setModuleConfig(destNum: Int, config: org.meshtastic.proto.ModuleConfig, packetId: Int)
    suspend fun setFixedPosition(destNum: Int, position: Position)
    suspend fun setRingtone(destNum: Int, ringtone: String)
    suspend fun setCannedMessages(destNum: Int, messages: String)

    // Admin operations
    suspend fun reboot(destNum: Int, packetId: Int)
    suspend fun shutdown(destNum: Int, packetId: Int)
    suspend fun factoryReset(destNum: Int, packetId: Int)
    suspend fun nodedbReset(destNum: Int, packetId: Int, preserveFavorites: Boolean)
    suspend fun removeByNodenum(packetId: Int, nodeNum: Int)

    // Batch editing
    suspend fun beginEditSettings(destNum: Int)
    suspend fun commitEditSettings(destNum: Int)

    // Helpers
    fun getPacketId(): Int
}

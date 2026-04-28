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
import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.WearableChannel
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.core.model.WearableNode
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.proto.Config.LoRaConfig

/**
 * Service that synchronizes phone data (nodes, messages) with the connected watch.
 * Listens to preferences and repository changes to push updates via the Wearable Data Layer.
 */
@Single
class WearableSyncService(
    private val context: Context,
    private val watchPrefs: WatchPrefs,
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val radioConfigRepository: RadioConfigRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }
    private val json = Json { ignoreUnknownKeys = true }

    init {
        Logger.d { "WearableSyncService initializing" }
        startSync()
    }

    private fun normalizeKey(contactKey: String): String {
        val isBroadcast = contactKey.endsWith(DataPacket.ID_BROADCAST)
        return if (!isBroadcast && contactKey.length > 9) {
            contactKey.takeLast(9) // Normalize PKI DM (8!ba012345) to legacy (!ba012345)
        } else contactKey
    }

    /** Triggers an immediate sync of all data to the watch. */
    fun forceSync(nodeId: String? = null) {
        Logger.d { "Manual sync requested${if (nodeId != null) " for node $nodeId" else ""}" }
        scope.launch {
            val myNodeNum = nodeRepository.ourNodeInfo.value?.num ?: 0
            val nodesMap = nodeRepository.nodeDBbyNum.value
            val wearableNodes = nodesMap.values
                .filter { it.num != myNodeNum }
                .map { node ->
                    WearableNode(
                        num = node.num,
                        name = node.user.long_name,
                        shortName = node.user.short_name,
                        online = node.isOnline,
                        battery = node.batteryLevel,
                        snr = node.snr.takeIf { it != Float.MAX_VALUE },
                        lastSeen = node.lastHeard.toLong() * 1000,
                        favorite = node.isFavorite,
                    )
                }
            syncNodes(wearableNodes, nodeId)

            val channelSet = radioConfigRepository.channelSetFlow.first()
            val loraConfig = channelSet.lora_config ?: LoRaConfig()
            val wearableChannels = channelSet.settings.mapIndexed { index, settings ->
                val channel = Channel(settings, loraConfig)
                val contactKey = "$index${DataPacket.ID_BROADCAST}"
                WearableChannel(index = index, name = channel.name, contactKey = contactKey)
            }
            syncChannels(wearableChannels, nodeId)

            val contacts = packetRepository.getContacts().first()
            val allWearableMessages = mutableListOf<WearableMessage>()

            // 1. Placeholders
            nodesMap.values
                .filter { it.num != myNodeNum }
                .forEach { node ->
                    val contactKey = DataPacket.nodeNumToDefaultId(node.num)
                    allWearableMessages.add(
                        WearableMessage(
                            uuid = node.num.toLong(),
                            contactKey = contactKey,
                            senderName = node.user.long_name,
                            senderShortName = node.user.short_name,
                            text = "No messages yet",
                            isMe = false,
                            timestamp = 0L,
                            address = contactKey,
                            channelIndex = 0,
                            status = MessageStatus.RECEIVED
                        )
                    )
                }
            
            // 2. Real messages
            contacts.keys.take(20).forEach { contactKey ->
                val channelIndex = contactKey[0].digitToIntOrNull()
                val isBroadcast = contactKey.endsWith(DataPacket.ID_BROADCAST)
                val normalizedKey = normalizeKey(contactKey)
                
                if (!isBroadcast && DataPacket.idToDefaultNodeNum(normalizedKey) == myNodeNum) {
                    return@forEach
                }

                val threadInfo = if (isBroadcast && channelIndex != null) {
                    val name = wearableChannels.getOrNull(channelIndex)?.name ?: "Channel $channelIndex"
                    name to name.take(2).uppercase()
                } else {
                    // For DMs, use the contact's name as the thread title
                    val contactNode = nodesMap[DataPacket.idToDefaultNodeNum(normalizedKey)]
                    (contactNode?.user?.long_name ?: normalizedKey) to (contactNode?.user?.short_name ?: "??")
                }

                packetRepository.getMessagesFrom(
                    contact = contactKey, 
                    limit = 10, 
                    getNode = { id -> nodesMap[DataPacket.idToDefaultNodeNum(id) ?: 0] ?: nodeRepository.getNode(id ?: "") }
                ).first().forEach { msg ->
                    allWearableMessages.add(
                        WearableMessage(
                            uuid = msg.uuid,
                            contactKey = normalizedKey,
                            senderName = threadInfo.first,
                            senderShortName = threadInfo.second,
                            text = msg.text,
                            isMe = msg.fromLocal,
                            timestamp = msg.receivedTime,
                            address = if (isBroadcast) null else normalizedKey,
                            channelIndex = channelIndex ?: 0,
                            status = msg.status ?: MessageStatus.RECEIVED
                        )
                    )
                }
            }
            
            val finalMessages = allWearableMessages
                .sortedByDescending { it.timestamp }
                .distinctBy { it.contactKey }
            
            syncMessages(finalMessages, nodeId)
        }
    }

    private fun startSync() {
        // Sync when manually requested from phone
        watchPrefs.syncRequest
            .onEach { forceSync() }
            .launchIn(scope)

        // Sync node list when enabled
        combine(
            watchPrefs.pushToWatchEnabled,
            watchPrefs.syncNodesEnabled,
            nodeRepository.nodeDBbyNum,
            nodeRepository.ourNodeInfo
        ) { push, sync, nodes, ourInfo ->
            if (push && sync) {
                val myNodeNum = ourInfo?.num ?: 0
                val wearableNodes = nodes.values
                    .filter { it.num != myNodeNum }
                    .map { node ->
                        WearableNode(
                            num = node.num,
                            name = node.user.long_name,
                            shortName = node.user.short_name,
                            online = node.isOnline,
                            battery = node.batteryLevel,
                            snr = node.snr.takeIf { it != Float.MAX_VALUE },
                            lastSeen = node.lastHeard.toLong() * 1000,
                            favorite = node.isFavorite,
                        )
                    }
                syncNodes(wearableNodes)
            }
        }.launchIn(scope)

        // Sync messages and channels when enabled
        watchPrefs.pushToWatchEnabled
            .onEach { if (it) forceSync() }
            .launchIn(scope)

        watchPrefs.syncMessagesEnabled
            .onEach { if (it) forceSync() }
            .launchIn(scope)

        nodeRepository.nodeDBbyNum
            .onEach { forceSync() }
            .launchIn(scope)
            
        packetRepository.getContacts()
            .onEach { forceSync() }
            .launchIn(scope)
    }

    companion object {
        private const val PATH_NODES = "/nodes"
        private const val PATH_MESSAGES = "/messages"
        private const val PATH_CHANNELS = "/channels"
    }

    private fun syncNodes(nodes: List<WearableNode>, targetNodeId: String? = null) {
        Logger.d { "Syncing ${nodes.size} nodes to wearable" }
        val nodesJson = json.encodeToString(nodes)
        
        // Use Data Layer for same-package sync
        val putDataMapReq = PutDataMapRequest.create(PATH_NODES).apply {
            dataMap.putString("nodes_json", nodesJson)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
            .addOnSuccessListener { Logger.d { "Nodes sync (Data Layer) success" } }
            .addOnFailureListener { e -> Logger.e(e) { "Nodes sync (Data Layer) failed" } }

        // Fallback: Use Message API for different-package sync
        scope.launch {
            try {
                val targetNodes = targetNodeId?.let { listOf(it) } 
                                 ?: nodeClient.connectedNodes.await().map { it.id }
                
                targetNodes.forEach { id ->
                    messageClient.sendMessage(id, PATH_NODES, nodesJson.toByteArray()).await()
                }
                Logger.d { "Nodes sync (Message API) success for ${targetNodes.size} nodes" }
            } catch (e: Exception) {
                Logger.e(e) { "Nodes sync (Message API) failed" }
            }
        }
    }

    private fun syncMessages(messages: List<WearableMessage>, targetNodeId: String? = null) {
        Logger.d { "Syncing ${messages.size} messages to wearable" }
        val messagesJson = json.encodeToString(messages)
        
        // Use Data Layer for same-package sync
        val putDataMapReq = PutDataMapRequest.create(PATH_MESSAGES).apply {
            dataMap.putString("messages_json", messagesJson)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
            .addOnSuccessListener { Logger.d { "Messages sync (Data Layer) success" } }
            .addOnFailureListener { e -> Logger.e(e) { "Messages sync (Data Layer) failed" } }

        // Fallback: Use Message API for different-package sync
        scope.launch {
            try {
                val targetNodes = targetNodeId?.let { listOf(it) } 
                                 ?: nodeClient.connectedNodes.await().map { it.id }
                
                targetNodes.forEach { id ->
                    messageClient.sendMessage(id, PATH_MESSAGES, messagesJson.toByteArray()).await()
                }
                Logger.d { "Messages sync (Message API) success for ${targetNodes.size} nodes" }
            } catch (e: Exception) {
                Logger.e(e) { "Messages sync (Message API) failed" }
            }
        }
    }

    private fun syncChannels(channels: List<WearableChannel>, targetNodeId: String? = null) {
        Logger.d { "Syncing ${channels.size} channels to wearable" }
        val channelsJson = json.encodeToString(channels)
        
        val putDataMapReq = PutDataMapRequest.create(PATH_CHANNELS).apply {
            dataMap.putString("channels_json", channelsJson)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)

        scope.launch {
            try {
                val targetNodes = targetNodeId?.let { listOf(it) } 
                                 ?: nodeClient.connectedNodes.await().map { it.id }
                
                targetNodes.forEach { id ->
                    messageClient.sendMessage(id, PATH_CHANNELS, channelsJson.toByteArray()).await()
                }
            } catch (e: Exception) {
                Logger.e(e) { "Channels sync failed" }
            }
        }
    }
}

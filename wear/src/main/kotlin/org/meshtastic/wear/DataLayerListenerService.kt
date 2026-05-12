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
package org.meshtastic.wear

import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import org.meshtastic.core.model.WearableChannel
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.core.model.WearableNode
import org.meshtastic.wear.presentation.model.WearableChannelRepository
import org.meshtastic.wear.presentation.model.WearableMessageRepository
import org.meshtastic.wear.presentation.model.WearableNodeRepository

/**
 * Listener that receives data updates from the phone via the Wearable Data Layer and Message API.
 */
class DataLayerListenerService : WearableListenerService() {

    private val nodeRepository: WearableNodeRepository by inject()
    private val messageRepository: WearableMessageRepository by inject()
    private val channelRepository: WearableChannelRepository by inject()
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate() {
        super.onCreate()
        Logger.d { "DataLayerListenerService created" }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Logger.d { "onDataChanged: ${dataEvents.count} events" }
        for (event in dataEvents) {
            Logger.d { "Event: type=${event.type}, path=${event.dataItem.uri.path}" }
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                when (path) {
                    "/nodes" -> {
                        val nodesJson = dataMap.getString("nodes_json")
                        Logger.d { "Received nodes JSON via Data Layer, length=${nodesJson?.length}" }
                        if (nodesJson != null) {
                            handleNodesJson(nodesJson)
                        }
                    }
                    "/messages" -> {
                        val messagesJson = dataMap.getString("messages_json")
                        Logger.d { "Received messages JSON via Data Layer, length=${messagesJson?.length}" }
                        if (messagesJson != null) {
                            handleMessagesJson(messagesJson)
                        }
                    }
                    "/channels" -> {
                        val channelsJson = dataMap.getString("channels_json")
                        Logger.d { "Received channels JSON via Data Layer, length=${channelsJson?.length}" }
                        if (channelsJson != null) {
                            handleChannelsJson(channelsJson)
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Logger.d { "onMessageReceived: path=${messageEvent.path}, dataLength=${messageEvent.data.size}" }
        when (messageEvent.path) {
            "/nodes" -> {
                val nodesJson = String(messageEvent.data)
                handleNodesJson(nodesJson)
            }
            "/messages" -> {
                val messagesJson = String(messageEvent.data)
                handleMessagesJson(messagesJson)
            }
            "/channels" -> {
                val channelsJson = String(messageEvent.data)
                handleChannelsJson(channelsJson)
            }
        }
    }

    private fun handleNodesJson(jsonStr: String) {
        try {
            val nodes = json.decodeFromString<List<WearableNode>>(jsonStr)
            Logger.d { "Successfully decoded ${nodes.size} nodes" }
            nodeRepository.updateNodes(nodes)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to decode nodes JSON" }
        }
    }

    private fun handleMessagesJson(jsonStr: String) {
        try {
            val messages = json.decodeFromString<List<WearableMessage>>(jsonStr)
            Logger.d { "Successfully decoded ${messages.size} messages" }
            messageRepository.updateMessages(messages)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to decode messages JSON" }
        }
    }

    private fun handleChannelsJson(jsonStr: String) {
        try {
            val channels = json.decodeFromString<List<WearableChannel>>(jsonStr)
            Logger.d { "Successfully decoded ${channels.size} channels" }
            channelRepository.updateChannels(channels)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to decode channels JSON" }
        }
    }
}

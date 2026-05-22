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
package org.meshtastic.feature.car.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Factory
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.QuickChatActionRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.feature.car.model.CarSessionState
import org.meshtastic.feature.car.model.ChannelUi
import org.meshtastic.feature.car.model.ConversationUi
import org.meshtastic.feature.car.model.MessagingUiState
import org.meshtastic.feature.car.model.NodeDashboardUiState
import org.meshtastic.feature.car.model.NodeUi
import org.meshtastic.feature.car.model.SignalQuality
import org.meshtastic.feature.car.model.TopologyHeader
import org.meshtastic.feature.car.util.CarTtsEngine
import java.util.concurrent.ConcurrentHashMap

/** Snapshot of a message for car display (avoids leaking domain models to UI). */
data class MessageSnapshot(
    val id: Int,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean,
)

/**
 * Bridges repository data flows to car screen presentation state. Created per car session — destroyed when session
 * ends.
 */
@Factory
@Suppress("TooManyFunctions")
class CarStateCoordinator(
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val serviceRepository: ServiceRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val quickChatActionRepository: QuickChatActionRepository,
    private val commandSender: CommandSender,
    private val ttsEngine: CarTtsEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _sessionState =
        MutableStateFlow(
            CarSessionState(
                connectionStatus = ConnectionState.Disconnected,
                onlineNodeCount = 0,
                lastMessageTime = null,
                activeEmergencies = emptyList(),
                meshName = null,
            ),
        )
    val sessionState: StateFlow<CarSessionState> = _sessionState.asStateFlow()

    private val _messagingState =
        MutableStateFlow(
            MessagingUiState(
                channels = emptyList(),
                selectedChannelIndex = 0,
                conversations = emptyList(),
                emergencySpotlight = null,
            ),
        )
    val messagingState: StateFlow<MessagingUiState> = _messagingState.asStateFlow()

    private val _nodeDashboardState =
        MutableStateFlow(NodeDashboardUiState(nodes = emptyList(), topologyHeader = TopologyHeader(0, 0, null)))
    val nodeDashboardState: StateFlow<NodeDashboardUiState> = _nodeDashboardState.asStateFlow()

    private val _quickChatActions = MutableStateFlow<List<String>>(emptyList())
    val quickChatActions: StateFlow<List<String>> = _quickChatActions.asStateFlow()

    private val selectedChannel = MutableStateFlow(0)

    fun start() {
        collectConnectionState()
        collectNodeData()
        collectMessagingData()
        collectQuickChat()
    }

    fun selectChannel(index: Int) {
        selectedChannel.value = index
        _messagingState.value = _messagingState.value.copy(selectedChannelIndex = index)
    }

    suspend fun getMessagesFlow(contactKey: String): Flow<List<MessageSnapshot>> = packetRepository
        .getMessagesFrom(
            contact = contactKey,
            limit = MAX_MESSAGES_PER_CONVERSATION,
            includeFiltered = false,
            getNode = { nodeId -> resolveNode(nodeId) },
        )
        .map { messages ->
            messages.map { msg ->
                MessageSnapshot(
                    id = msg.packetId,
                    senderName = msg.node.user.long_name.ifEmpty { "Unknown" },
                    text = msg.text,
                    timestamp = msg.receivedTime,
                    isFromMe = msg.fromLocal,
                )
            }
        }

    fun sendMessage(contactKey: String, text: String) {
        val packet =
            DataPacket(
                to = contactKey,
                bytes = text.encodeToByteArray().toByteString(),
                dataType = DATA_TYPE_TEXT,
                channel = selectedChannel.value,
            )
        commandSender.sendData(packet)
    }

    fun readMessagesAloud(contactKey: String) {
        val messages = messagesCache[contactKey] ?: return
        messages.takeLast(READ_ALOUD_LIMIT).forEach { msg ->
            if (!msg.isFromMe) {
                ttsEngine.readAloud(msg.senderName, msg.text)
            }
        }
    }

    private val messagesCache = ConcurrentHashMap<String, List<MessageSnapshot>>()

    fun cacheMessages(contactKey: String, messages: List<MessageSnapshot>) {
        messagesCache[contactKey] = messages
    }

    private suspend fun resolveNode(nodeId: String?): Node {
        val nodes = nodeRepository.nodeDBbyNum.value
        return nodes.values.find { it.user.id == nodeId } ?: Node(num = 0)
    }

    fun destroy() {
        scope.cancel()
        ttsEngine.shutdown()
    }

    private fun collectConnectionState() {
        scope.launch {
            serviceRepository.connectionState.collect { state ->
                _sessionState.value = _sessionState.value.copy(connectionStatus = state)
            }
        }
    }

    private fun collectNodeData() {
        scope.launch {
            combine(nodeRepository.nodeDBbyNum, nodeRepository.onlineNodeCount) { nodeMap, onlineCount ->
                val nodes =
                    nodeMap.values
                        .map { node -> node.toNodeUi() }
                        .sortedWith(compareByDescending<NodeUi> { it.isOnline }.thenByDescending { it.lastHeard })
                val totalCount = nodeMap.size
                val meshName = nodeRepository.myNodeInfo.value?.firmwareVersion

                _nodeDashboardState.value =
                    NodeDashboardUiState(
                        nodes = nodes,
                        topologyHeader =
                        TopologyHeader(totalNodes = totalCount, onlineNodes = onlineCount, meshName = meshName),
                    )
                _sessionState.value = _sessionState.value.copy(onlineNodeCount = onlineCount)
            }
                .collect {}
        }
    }

    private fun collectMessagingData() {
        scope.launch {
            combine(packetRepository.getContacts(), radioConfigRepository.channelSetFlow) { contacts, channelSet ->
                val channels =
                    channelSet.settings.mapIndexed { index, settings ->
                        val channel = Channel(settings = settings)
                        ChannelUi(
                            index = index,
                            name = channel.name,
                            unreadCount = 0, // will be updated per-channel
                        )
                    }

                val conversations =
                    contacts.entries
                        .take(MAX_CONVERSATIONS)
                        .map { (contactKey, packet) ->
                            val senderNode =
                                nodeRepository.nodeDBbyNum.value.values.find { it.user.id == packet.from }
                            ConversationUi(
                                contactKey = contactKey,
                                displayName = senderNode?.user?.long_name ?: contactKey,
                                lastMessage = packet.bytes?.utf8() ?: "",
                                lastMessageTime = packet.time,
                                unreadCount = 0,
                                isEmergency = false,
                            )
                        }
                        .sortedByDescending { it.lastMessageTime }

                _messagingState.value =
                    MessagingUiState(
                        channels = channels,
                        selectedChannelIndex = selectedChannel.value,
                        conversations = conversations,
                        emergencySpotlight = null,
                    )

                // Update last message time in session state
                val lastTime = conversations.maxOfOrNull { it.lastMessageTime }
                if (lastTime != null) {
                    _sessionState.value = _sessionState.value.copy(lastMessageTime = lastTime)
                }
            }
                .collect {}
        }
    }

    private fun collectQuickChat() {
        scope.launch {
            quickChatActionRepository.getAllActions().collect { actions ->
                _quickChatActions.value = actions.map { action -> action.message }
            }
        }
    }

    private fun Node.toNodeUi(): NodeUi = NodeUi(
        nodeNum = num,
        longName = user.long_name.ifEmpty { "Unknown" },
        shortName = user.short_name.ifEmpty { "?" },
        signalQuality = determineSignalQuality(snr, rssi),
        batteryPercent = batteryLevel?.takeIf { it in 1..BATTERY_MAX_PERCENT },
        isOnline = isOnline,
        lastHeard = lastHeard.toLong() * SECONDS_TO_MILLIS,
        hasPosition = validPosition != null,
    )

    companion object {
        private const val MAX_CONVERSATIONS = 10
        private const val MAX_MESSAGES_PER_CONVERSATION = 20
        private const val READ_ALOUD_LIMIT = 3
        private const val DATA_TYPE_TEXT = 1
        private const val SECONDS_TO_MILLIS = 1000L
        private const val BATTERY_MAX_PERCENT = 100

        // Thresholds aligned with core/ui LoraSignalIndicator.kt
        private const val SNR_GOOD_THRESHOLD = -7f
        private const val SNR_FAIR_THRESHOLD = -15f
        private const val RSSI_GOOD_THRESHOLD = -115
        private const val RSSI_FAIR_THRESHOLD = -126

        @Suppress("MagicNumber")
        private fun determineSignalQuality(snr: Float, rssi: Int): SignalQuality = when {
            snr == Float.MAX_VALUE || rssi == Int.MAX_VALUE -> SignalQuality.NONE
            snr > SNR_GOOD_THRESHOLD && rssi > RSSI_GOOD_THRESHOLD -> SignalQuality.EXCELLENT
            snr > SNR_GOOD_THRESHOLD && rssi > RSSI_FAIR_THRESHOLD -> SignalQuality.GOOD
            snr > SNR_FAIR_THRESHOLD && rssi > RSSI_GOOD_THRESHOLD -> SignalQuality.GOOD
            snr > SNR_FAIR_THRESHOLD -> SignalQuality.FAIR
            else -> SignalQuality.BAD
        }
    }
}

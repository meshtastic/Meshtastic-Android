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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.QuickChatActionRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.feature.car.model.CarSessionState
import org.meshtastic.feature.car.model.ChannelUi
import org.meshtastic.feature.car.model.ConnectionStatus
import org.meshtastic.feature.car.model.ConversationUi
import org.meshtastic.feature.car.model.MessagingUiState
import org.meshtastic.feature.car.model.NodeDashboardUiState
import org.meshtastic.feature.car.model.NodeUi
import org.meshtastic.feature.car.model.SignalQuality
import org.meshtastic.feature.car.model.TopologyHeader

/**
 * Bridges repository data flows to car screen presentation state. Created per car session — destroyed when session
 * ends.
 */
@Factory
class CarStateCoordinator(
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val serviceRepository: ServiceRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val quickChatActionRepository: QuickChatActionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _sessionState =
        MutableStateFlow(
            CarSessionState(
                connectionStatus = ConnectionStatus.DISCONNECTED,
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

    private var selectedChannelIndex = 0

    fun start() {
        collectConnectionState()
        collectNodeData()
        collectMessagingData()
        collectQuickChat()
    }

    fun selectChannel(index: Int) {
        selectedChannelIndex = index
        _messagingState.value = _messagingState.value.copy(selectedChannelIndex = index)
    }

    fun destroy() {
        scope.cancel()
    }

    private fun collectConnectionState() {
        scope.launch {
            serviceRepository.connectionState.collect { state ->
                val status =
                    when (state) {
                        ConnectionState.Connected -> ConnectionStatus.CONNECTED
                        ConnectionState.Connecting -> ConnectionStatus.CONNECTING
                        else -> ConnectionStatus.DISCONNECTED
                    }
                _sessionState.value = _sessionState.value.copy(connectionStatus = status)
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
                        selectedChannelIndex = selectedChannelIndex,
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
        signalQuality = snrToSignalQuality(snr),
        batteryPercent = batteryLevel?.takeIf { it in 1..BATTERY_MAX_PERCENT },
        isOnline = isOnline,
        lastHeard = lastHeard.toLong() * SECONDS_TO_MILLIS,
        hasPosition = validPosition != null,
    )

    companion object {
        private const val MAX_CONVERSATIONS = 10
        private const val SECONDS_TO_MILLIS = 1000L
        private const val BATTERY_MAX_PERCENT = 100
        private const val SNR_EXCELLENT = 10f
        private const val SNR_GOOD = 5f
        private const val SNR_FAIR = 0f

        private fun snrToSignalQuality(snr: Float): SignalQuality = when {
            snr == Float.MAX_VALUE -> SignalQuality.UNKNOWN
            snr >= SNR_EXCELLENT -> SignalQuality.EXCELLENT
            snr >= SNR_GOOD -> SignalQuality.GOOD
            snr >= SNR_FAIR -> SignalQuality.FAIR
            else -> SignalQuality.POOR
        }
    }
}

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

import androidx.annotation.VisibleForTesting
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.feature.car.model.CarLocalStats
import org.meshtastic.feature.car.model.CarSessionState
import org.meshtastic.feature.car.model.ChannelUi
import org.meshtastic.feature.car.model.ConversationUi
import org.meshtastic.feature.car.model.EmergencyAlert
import org.meshtastic.feature.car.model.MessagingUiState
import org.meshtastic.feature.car.model.NodeDashboardUiState
import org.meshtastic.feature.car.model.TopologyHeader
import org.meshtastic.feature.car.util.CarScreenDataBuilder
import org.meshtastic.feature.car.util.MessageFilter
import org.meshtastic.proto.PortNum

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
    private val sendMessageUseCase: SendMessageUseCase,
    private val messageFilter: MessageFilter,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.e(tag = "CarStateCoordinator", throwable = throwable) { "Unhandled error in car state flow" }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)
    private var nodeJob: Job? = null
    private var messagingJob: Job? = null

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

    private val _localStatsState = MutableStateFlow(CarLocalStats())
    val localStatsState: StateFlow<CarLocalStats> = _localStatsState.asStateFlow()

    /** Test seam: push presentation state directly without driving the repositories (see CarScreensTest). */
    @VisibleForTesting
    internal fun setStateForTest(
        session: CarSessionState = _sessionState.value,
        messaging: MessagingUiState = _messagingState.value,
        nodes: NodeDashboardUiState = _nodeDashboardState.value,
        stats: CarLocalStats = _localStatsState.value,
    ) {
        _sessionState.value = session
        _messagingState.value = messaging
        _nodeDashboardState.value = nodes
        _localStatsState.value = stats
    }

    /**
     * Emits an [EmergencyAlert] for each new incoming ALERT_APP packet. Sourced from the contacts flow (last packet per
     * contact), deduped by packet id so the same alert isn't re-raised.
     */
    val emergencyAlerts: kotlinx.coroutines.flow.Flow<EmergencyAlert> =
        packetRepository
            .getContacts()
            .mapNotNull { contacts ->
                contacts.values.filter { it.dataType == PortNum.ALERT_APP.value }.maxByOrNull { it.time }
            }
            .distinctUntilChangedBy { it.id }
            .map { it.toEmergencyAlert() }

    private fun DataPacket.toEmergencyAlert(): EmergencyAlert {
        val entry = nodeRepository.nodeDBbyNum.value.entries.find { it.value.user.id == from }
        return EmergencyAlert(
            nodeNum = entry?.key ?: 0,
            nodeName = entry?.value?.user?.long_name ?: from ?: "Unknown",
            message = alert ?: "",
            timestamp = time,
            isActive = true,
        )
    }

    private val selectedChannel = MutableStateFlow(0)

    fun start() {
        collectConnectionState()
        collectNodeData()
        collectMessagingData()
        collectLocalStats()
    }

    fun refresh() {
        nodeJob?.cancel()
        messagingJob?.cancel()
        collectNodeData()
        collectMessagingData()
    }

    fun selectChannel(index: Int) {
        selectedChannel.value = index
        _messagingState.value = _messagingState.value.copy(selectedChannelIndex = index)
    }

    fun sendMessage(contactKey: String, text: String): Boolean {
        val validation = messageFilter.validateOutgoing(text)
        if (validation is MessageFilter.ValidationResult.TooLong) {
            return false
        }
        scope.launch { sendMessageUseCase(text, contactKey) }
        return true
    }

    fun markAsRead(contactKey: String) {
        scope.launch {
            runCatching { packetRepository.clearUnreadCount(contactKey, System.currentTimeMillis()) }
                .onFailure { throwable ->
                    Logger.e(tag = "CarStateCoordinator", throwable = throwable) { "Failed to mark as read" }
                }
        }
    }

    /**
     * Ensures a DM conversation appears in the messaging list for the given [contactKey]. If the contact doesn't have
     * an existing conversation, adds a placeholder entry so the ConversationItem is visible for voice reply.
     */
    fun ensureDmConversation(contactKey: String, displayName: String, placeholderMessage: String) {
        val current = _messagingState.value
        if (current.conversations.any { it.contactKey == contactKey }) return
        val placeholder =
            ConversationUi(
                contactKey = contactKey,
                displayName = displayName,
                lastMessage = placeholderMessage,
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0,
                isEmergency = false,
            )
        _messagingState.value = current.copy(conversations = listOf(placeholder) + current.conversations)
    }

    fun destroy() {
        scope.cancel()
    }

    private fun collectConnectionState() {
        scope.launch {
            serviceRepository.connectionState.collect { state ->
                _sessionState.value = _sessionState.value.copy(connectionStatus = state)
            }
        }
    }

    private fun collectNodeData() {
        nodeJob =
            scope.launch {
                combine(
                    nodeRepository.nodeDBbyNum,
                    nodeRepository.onlineNodeCount,
                    radioConfigRepository.localConfigFlow,
                ) { nodeMap, onlineCount, localConfig ->
                    val nodes = CarScreenDataBuilder.sortNodes(nodeMap.values, localConfig.lora?.modem_preset)
                    val totalCount = nodeMap.size
                    val meshName = nodeRepository.ourNodeInfo.value?.user?.long_name

                    _nodeDashboardState.value =
                        NodeDashboardUiState(
                            nodes = nodes,
                            topologyHeader =
                            TopologyHeader(
                                totalNodes = totalCount,
                                onlineNodes = onlineCount,
                                meshName = meshName,
                            ),
                        )
                    _sessionState.value = _sessionState.value.copy(onlineNodeCount = onlineCount)
                }
                    .collect {}
            }
    }

    private fun collectMessagingData() {
        messagingJob =
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
                        CarScreenDataBuilder.sortConversations(
                            contacts.entries.map { (contactKey, packet) ->
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
                            },
                        )
                            .take(CarScreenDataBuilder.MAX_CONVERSATIONS)

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

    private fun collectLocalStats() {
        scope.launch {
            combine(nodeRepository.localStats, nodeRepository.nodeDBbyNum) { stats, nodeMap ->
                val ourNode =
                    nodeRepository.ourNodeInfo.value
                        ?: nodeRepository.myNodeInfo.value?.myNodeNum?.let(nodeMap::get)
                CarScreenDataBuilder.buildLocalStats(ourNode = ourNode, stats = stats, allNodes = nodeMap.values)
            }
                .collect { _localStatsState.value = it }
        }
    }
}

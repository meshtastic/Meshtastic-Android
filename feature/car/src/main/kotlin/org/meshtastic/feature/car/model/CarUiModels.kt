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
package org.meshtastic.feature.car.model

import org.meshtastic.core.model.ConnectionState

data class CarSessionState(
    val connectionStatus: ConnectionState,
    val onlineNodeCount: Int,
    val lastMessageTime: Long?,
    val activeEmergencies: List<EmergencyAlert>,
    val meshName: String?,
)

data class MessagingUiState(
    val channels: List<ChannelUi>,
    val selectedChannelIndex: Int,
    val conversations: List<ConversationUi>,
    val emergencySpotlight: List<EmergencyAlert>?,
)

data class ChannelUi(val index: Int, val name: String, val unreadCount: Int)

data class ConversationUi(
    val contactKey: String,
    val displayName: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int,
    val isEmergency: Boolean,
)

data class NodeDashboardUiState(val nodes: List<NodeUi>, val topologyHeader: TopologyHeader)

data class NodeUi(
    val nodeNum: Int,
    val longName: String,
    val shortName: String,
    val signalQuality: SignalQuality,
    val batteryPercent: Int?,
    val isOnline: Boolean,
    val lastHeard: Long,
    val hasPosition: Boolean,
)

enum class SignalQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    UNKNOWN,
}

data class TopologyHeader(val totalNodes: Int, val onlineNodes: Int, val meshName: String?)

data class EmergencyAlert(
    val nodeNum: Int,
    val nodeName: String,
    val message: String,
    val timestamp: Long,
    val isActive: Boolean,
)

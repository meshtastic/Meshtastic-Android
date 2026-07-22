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
package org.meshtastic.core.repository

import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Telemetry

const val SERVICE_NOTIFY_ID = 101

/**
 * Mesh-domain notification builder. Provides high-level operations for the message arrival, waypoint, reaction, new
 * node, low-battery, and client notification flows specific to this app. Implementations are expected to render the
 * platform notification themselves; the generic dispatch primitive is [NotificationManager] (which posts/cancels opaque
 * [Notification] records and is *not* domain-aware).
 */
@Suppress("TooManyFunctions")
interface MeshNotificationManager {
    fun clearNotifications()

    fun initChannels()

    fun updateServiceStateNotification(state: ConnectionState, telemetry: Telemetry?)

    suspend fun updateMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean = false,
    )

    suspend fun updateWaypointNotification(
        contactKey: String,
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean = false,
    )

    suspend fun updateReactionNotification(
        contactKey: String,
        name: String,
        emoji: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean = false,
    )

    fun showAlertNotification(contactKey: String, name: String, alert: String)

    fun showNewNodeSeenNotification(node: Node)

    fun showOrUpdateLowBatteryNotification(node: Node, isRemote: Boolean)

    fun showClientNotification(clientNotification: ClientNotification)

    fun cancelMessageNotification(contactKey: String)

    /**
     * Called after an inline notification reply has been sent and persisted. Platforms that can should re-post the
     * conversation notification silently with the sent reply appended — the MessagingStyle confirmation flow — so the
     * RemoteInput spinner resolves with visible feedback instead of the notification vanishing. The default falls back
     * to dismissing the conversation, which also resolves the spinner.
     */
    suspend fun refreshConversationAfterReply(contactKey: String) = cancelMessageNotification(contactKey)

    fun cancelLowBatteryNotification(node: Node)

    fun clearClientNotification(notification: ClientNotification)
}

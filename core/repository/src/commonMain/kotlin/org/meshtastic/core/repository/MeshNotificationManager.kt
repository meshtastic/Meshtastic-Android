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
import org.meshtastic.core.model.FirmwareUpdateNotice
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.model.Node
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Telemetry

const val SERVICE_NOTIFY_ID = 101

/**
 * The one notification API for shared code: every notification the app posts or cancels goes through here, and each
 * platform renders it with its own channels, styles and tap targets. [NotificationManager] is the desktop's dispatch
 * primitive underneath its implementation, not a second way in.
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

    suspend fun showAlertNotification(contactKey: String, name: String, alert: String)

    suspend fun showMeshBeaconNotification(offer: MeshBeaconOffer)

    /** [title] arrives resolved: the caller revalidates [node]'s identity right before posting, with no suspension. */
    suspend fun showNewNodeSeenNotification(node: Node, title: String)

    fun cancelNewNodeNotification(nodeNum: Int)

    /** Posts the low-battery warning for [node], alerting once. */
    suspend fun showLowBatteryNotification(node: Node, isRemote: Boolean)

    /** Refreshes a still-showing low-battery warning with [node]'s current level; never re-posts a dismissed one. */
    suspend fun updateLowBatteryNotification(node: Node, isRemote: Boolean)

    fun cancelLowBatteryNotification(node: Node)

    /** [title] and [severity] come from the notification's kind, which shared code classifies once for every platform. */
    suspend fun showClientNotification(clientNotification: ClientNotification, title: String, severity: Notification.Type)

    fun clearClientNotification(clientNotification: ClientNotification)

    /** True when the platform presents [clientNotification] natively, so the in-app modal must not show it too. */
    fun suppressClientNotificationModal(clientNotification: ClientNotification): Boolean = false

    /** Returns true only when the platform accepted the notification, so the caller can record it as shown. */
    suspend fun showFirmwareUpdateNotification(notice: FirmwareUpdateNotice): Boolean

    /** Returns true only when the platform accepted the notification. [title] and [message] arrive resolved. */
    suspend fun showReconnectBlockedNotification(title: String, message: String): Boolean

    /**
     * Suspending because Android rebuilds the group summary here, and the summary's labels come from string resources —
     * resolving them must not block the caller's thread (see [org.meshtastic.core.resources.getStringSuspend]).
     */
    suspend fun cancelMessageNotification(contactKey: String)

    /**
     * Called after an inline notification reply has been sent and persisted. Platforms that can should re-post the
     * conversation notification silently with the sent reply appended — the MessagingStyle confirmation flow — so the
     * RemoteInput spinner resolves with visible feedback instead of the notification vanishing. The default falls back
     * to dismissing the conversation, which also resolves the spinner.
     */
    suspend fun refreshConversationAfterReply(contactKey: String) = cancelMessageNotification(contactKey)
}

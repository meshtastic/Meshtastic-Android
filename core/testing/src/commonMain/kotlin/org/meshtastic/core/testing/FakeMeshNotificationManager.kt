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
package org.meshtastic.core.testing

import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.FirmwareUpdateNotice
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Telemetry

/** Records every notification posted or cancelled. The `accepts*` flags stand in for a platform that declines. */
@Suppress("TooManyFunctions")
class FakeMeshNotificationManager : MeshNotificationManager {
    data class ClientPost(val notification: ClientNotification, val title: String, val severity: Notification.Type)

    var acceptsFirmwareUpdate = true
    var acceptsReconnectBlocked = true

    val meshBeacons = mutableListOf<MeshBeaconOffer>()
    val newNodes = mutableListOf<Node>()
    val cancelledNewNodes = mutableListOf<Int>()
    val lowBatteryShown = mutableListOf<Node>()
    val lowBatteryUpdated = mutableListOf<Node>()
    val lowBatteryCancelled = mutableListOf<Node>()
    val clientPosts = mutableListOf<ClientPost>()
    val clearedClientNotifications = mutableListOf<ClientNotification>()
    val firmwareUpdateNotices = mutableListOf<FirmwareUpdateNotice>()
    val reconnectBlocked = mutableListOf<Pair<String, String>>()
    var clearCount = 0
        private set

    override fun clearNotifications() {
        clearCount++
    }

    override fun initChannels() = Unit

    override fun updateServiceStateNotification(state: ConnectionState, telemetry: Telemetry?) = Unit

    override suspend fun updateMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) = Unit

    override suspend fun updateWaypointNotification(
        contactKey: String,
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean,
    ) = Unit

    override suspend fun updateReactionNotification(
        contactKey: String,
        name: String,
        emoji: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) = Unit

    override suspend fun showAlertNotification(contactKey: String, name: String, alert: String) = Unit

    override suspend fun showMeshBeaconNotification(offer: MeshBeaconOffer) {
        meshBeacons += offer
    }

    override suspend fun showNewNodeSeenNotification(node: Node, title: String) {
        newNodes += node
    }

    override fun cancelNewNodeNotification(nodeNum: Int) {
        cancelledNewNodes += nodeNum
    }

    override suspend fun showLowBatteryNotification(node: Node, isRemote: Boolean) {
        lowBatteryShown += node
    }

    override suspend fun updateLowBatteryNotification(node: Node, isRemote: Boolean) {
        lowBatteryUpdated += node
    }

    override fun cancelLowBatteryNotification(node: Node) {
        lowBatteryCancelled += node
    }

    override suspend fun showClientNotification(
        clientNotification: ClientNotification,
        title: String,
        severity: Notification.Type,
    ) {
        clientPosts += ClientPost(clientNotification, title, severity)
    }

    override fun clearClientNotification(clientNotification: ClientNotification) {
        clearedClientNotifications += clientNotification
    }

    override suspend fun showFirmwareUpdateNotification(notice: FirmwareUpdateNotice): Boolean {
        if (acceptsFirmwareUpdate) firmwareUpdateNotices += notice
        return acceptsFirmwareUpdate
    }

    override suspend fun showReconnectBlockedNotification(title: String, message: String): Boolean {
        if (acceptsReconnectBlocked) reconnectBlocked += title to message
        return acceptsReconnectBlocked
    }

    override suspend fun cancelMessageNotification(contactKey: String) = Unit
}

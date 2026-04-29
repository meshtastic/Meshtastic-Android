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
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Telemetry

/** A test double for [MeshServiceNotifications] that provides a no-op implementation. */
@Suppress("TooManyFunctions", "EmptyFunctionBlock")
open class FakeMeshServiceNotifications : MeshServiceNotifications {
    override fun clearNotifications() {}

    override fun initChannels() {}

    override fun updateServiceStateNotification(state: ConnectionState, telemetry: Telemetry?) {}

    override suspend fun updateMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {}

    override suspend fun updateWaypointNotification(
        contactKey: String,
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean,
    ) {}

    override suspend fun updateReactionNotification(
        contactKey: String,
        name: String,
        emoji: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {}

    override fun showAlertNotification(contactKey: String, name: String, alert: String) {}

    override fun showNewNodeSeenNotification(node: Node) {}

    override fun showOrUpdateLowBatteryNotification(node: Node, isRemote: Boolean) {}

    override fun showClientNotification(clientNotification: ClientNotification) {}

    override fun cancelMessageNotification(contactKey: String) {}

    override suspend fun markConversationRead(contactKey: String) {}

    override suspend fun appendOutgoingMessage(contactKey: String, text: String) {}

    override fun cancelLowBatteryNotification(node: Node) {}

    override fun clearClientNotification(notification: ClientNotification) {}
}

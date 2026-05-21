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
package org.meshtastic.app.ai.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * Response returned when a message is successfully sent via the mesh network.
 *
 * @property messageId The identifier assigned to the outgoing message.
 * @property channel The channel or destination the message was sent to.
 * @property timestamp The time the message was sent (epoch milliseconds).
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SendMessageResponse(val messageId: Int, val channel: String, val timestamp: Long)

/**
 * Response containing the current status of the Meshtastic mesh network.
 *
 * @property connectionState The current radio connection state (e.g., CONNECTED, DISCONNECTED).
 * @property onlineNodeCount The number of nodes currently online (heard within the last 15 minutes).
 * @property totalNodeCount The total number of nodes known to the network.
 * @property localBatteryLevel The battery percentage of the connected Meshtastic device (1-100), or null if
 *   unavailable.
 * @property localNodeName The display name of the local node, or null if not set.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MeshStatusResponse(
    val connectionState: String,
    val onlineNodeCount: Int,
    val totalNodeCount: Int,
    val localBatteryLevel: Int?,
    val localNodeName: String?,
)

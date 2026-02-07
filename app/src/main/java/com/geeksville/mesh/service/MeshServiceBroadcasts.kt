/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.service

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import co.touchlab.kermit.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.util.toPIIString
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.service.ServiceRepository
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshServiceBroadcasts
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val connectionStateHolder: ConnectionStateHandler,
    private val serviceRepository: ServiceRepository,
) {
    // A mapping of receiver class name to package name - used for explicit broadcasts
    private val clientPackages = mutableMapOf<String, String>()

    fun subscribeReceiver(receiverName: String, packageName: String) {
        clientPackages[receiverName] = packageName
    }

    /** Broadcast some received data Payload will be a DataPacket */
    fun broadcastReceivedData(payload: DataPacket) {
        val action = MeshService.actionReceived(payload.dataType)
        explicitBroadcast(Intent(action).putExtra(EXTRA_PAYLOAD, payload))

        // Also broadcast with the numeric port number for backwards compatibility with some apps
        val numericAction = actionReceived(payload.dataType.toString())
        if (numericAction != action) {
            explicitBroadcast(Intent(numericAction).putExtra(EXTRA_PAYLOAD, payload))
        }
    }

    fun broadcastNodeChange(info: NodeInfo) {
        Logger.d { "Broadcasting node change ${info.user?.toPIIString()}" }
        val intent = Intent(ACTION_NODE_CHANGE).putExtra(EXTRA_NODEINFO, info)
        explicitBroadcast(intent)
    }

    fun broadcastMessageStatus(p: DataPacket) = broadcastMessageStatus(p.id, p.status)

    fun broadcastMessageStatus(id: Int, status: MessageStatus?) {
        if (id == 0) {
            Logger.d { "Ignoring anonymous packet status" }
        } else {
            // Do not log, contains PII possibly
            // MeshService.Logger.d { "Broadcasting message status $p" }
            val intent =
                Intent(ACTION_MESSAGE_STATUS).apply {
                    putExtra(EXTRA_PACKET_ID, id)
                    putExtra(EXTRA_STATUS, status as Parcelable)
                }
            explicitBroadcast(intent)
        }
    }

    /** Broadcast our current connection status */
    fun broadcastConnection() {
        val connectionState = connectionStateHolder.connectionState.value
        // ATAK expects a String: "CONNECTED" or "DISCONNECTED"
        // It uses equalsIgnoreCase, but we'll use uppercase to be specific.
        val stateStr = connectionState.toString().uppercase(Locale.ROOT)

        val intent = Intent(ACTION_MESH_CONNECTED).apply { putExtra(EXTRA_CONNECTED, stateStr) }
        serviceRepository.setConnectionState(connectionState)
        explicitBroadcast(intent)

        if (connectionState == ConnectionState.Disconnected) {
            explicitBroadcast(Intent(ACTION_MESH_DISCONNECTED))
        }

        // Restore legacy action for other consumers (e.g. mesh_service_example)
        val legacyIntent =
            Intent(ACTION_CONNECTION_CHANGED).apply {
                putExtra(EXTRA_CONNECTED, stateStr)
                // Legacy boolean extra often expected by older implementations
                putExtra("connected", connectionState == ConnectionState.Connected)
            }
        explicitBroadcast(legacyIntent)
    }

    /**
     * See com.geeksville.mesh broadcast intents.
     *
     *     RECEIVED_OPAQUE  for data received from other nodes
     *     NODE_CHANGE  for new IDs appearing or disappearing
     *     ACTION_MESH_CONNECTED for losing/gaining connection to the packet radio
     *         Note: this is not the same as RadioInterfaceService.RADIO_CONNECTED_ACTION,
     *         because it implies we have assembled a warehouse valid node db.
     */
    private fun explicitBroadcast(intent: Intent) {
        context.sendBroadcast(
            intent,
        ) // We also do a regular (not explicit broadcast) so any context-registered rceivers will work
        clientPackages.forEach {
            intent.setClassName(it.value, it.key)
            context.sendBroadcast(intent)
        }
    }
}

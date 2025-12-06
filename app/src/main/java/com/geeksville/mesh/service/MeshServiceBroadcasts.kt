/*
 * Copyright (c) 2025 Meshtastic LLC
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
import dagger.hilt.android.qualifiers.ApplicationContext
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.util.toPIIString
import org.meshtastic.core.service.ServiceRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshServiceBroadcasts
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val connectionStateHolder: MeshServiceConnectionStateHolder,
    private val serviceRepository: ServiceRepository,
) {
    // A mapping of receiver class name to package name - used for explicit broadcasts
    private val clientPackages = mutableMapOf<String, String>()

    fun subscribeReceiver(receiverName: String, packageName: String) {
        clientPackages[receiverName] = packageName
    }

    /** Broadcast some received data Payload will be a DataPacket */
    fun broadcastReceivedData(payload: DataPacket) {
        explicitBroadcast(Intent(MeshService.actionReceived(payload.dataType)).putExtra(EXTRA_PAYLOAD, payload))
    }

    fun broadcastNodeChange(info: NodeInfo) {
        Timber.d("Broadcasting node change ${info.user?.toPIIString()}")
        val intent = Intent(MeshService.ACTION_NODE_CHANGE).putExtra(EXTRA_NODEINFO, info)
        explicitBroadcast(intent)
    }

    fun broadcastMessageStatus(p: DataPacket) = broadcastMessageStatus(p.id, p.status)

    fun broadcastMessageStatus(id: Int, status: MessageStatus?) {
        if (id == 0) {
            Timber.d("Ignoring anonymous packet status")
        } else {
            // Do not log, contains PII possibly
            // MeshService.Timber.d("Broadcasting message status $p")
            val intent =
                Intent(MeshService.ACTION_MESSAGE_STATUS).apply {
                    putExtra(EXTRA_PACKET_ID, id)
                    putExtra(EXTRA_STATUS, status as Parcelable)
                }
            explicitBroadcast(intent)
        }
    }

    /** Broadcast our current connection status */
    fun broadcastConnection() {
        val connectionState = connectionStateHolder.connectionState.value
        val intent = Intent(MeshService.ACTION_MESH_CONNECTED).putExtra(EXTRA_CONNECTED, connectionState.toString())
        serviceRepository.setConnectionState(connectionState)
        explicitBroadcast(intent)
    }

    /**
     * See com.geeksville.mesh broadcast intents.
     *
     *     RECEIVED_OPAQUE  for data received from other nodes
     *     NODE_CHANGE  for new IDs appearing or disappearing
     *     ACTION_MESH_CONNECTED for losing/gaining connection to the packet radio
     *         Note: this is not the same as RadioInterfaceService.RADIO_CONNECTED_ACTION,
     *         because it implies we have assembled a valid node db.
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

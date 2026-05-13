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
package org.meshtastic.core.service

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.util.toPIIString
import org.meshtastic.core.repository.ServiceRepository
import java.util.Locale
import org.meshtastic.core.repository.ServiceBroadcasts as SharedServiceBroadcasts

@Single
class ServiceBroadcasts(private val context: Context, private val serviceRepository: ServiceRepository) :
    SharedServiceBroadcasts {
    // A mapping of receiver class name to package name - used for explicit broadcasts.
    // ConcurrentHashMap because subscribeReceiver() is called from AIDL binder threads
    // while explicitBroadcast() iterates from coroutine contexts.
    private val clientPackages = java.util.concurrent.ConcurrentHashMap<String, String>()

    override fun subscribeReceiver(receiverName: String, packageName: String) {
        clientPackages[receiverName] = packageName
    }

    /** Broadcast some received data Payload will be a DataPacket */
    override fun broadcastReceivedData(dataPacket: DataPacket) {
        val action = MeshService.actionReceived(dataPacket.dataType)
        explicitBroadcast(Intent(action).putExtra(EXTRA_PAYLOAD, dataPacket))

        // Also broadcast with the numeric port number for backwards compatibility with some apps
        val numericAction = actionReceived(dataPacket.dataType.toString())
        if (numericAction != action) {
            explicitBroadcast(Intent(numericAction).putExtra(EXTRA_PAYLOAD, dataPacket))
        }
    }

    override fun broadcastNodeChange(node: Node) {
        Logger.d { "Broadcasting node change ${node.user.toPIIString()}" }
        val legacy = node.toLegacy()
        val intent = Intent(ACTION_NODE_CHANGE).putExtra(EXTRA_NODEINFO, legacy)
        explicitBroadcast(intent)
    }

    private fun Node.toLegacy(): NodeInfo = NodeInfo(
        num = num,
        user =
        org.meshtastic.core.model.MeshUser(
            id = user.id,
            longName = user.long_name,
            shortName = user.short_name,
            hwModel = user.hw_model,
            role = user.role.value,
        ),
        position =
        org.meshtastic.core.model
            .Position(
                latitude = latitude,
                longitude = longitude,
                altitude = position.altitude ?: 0,
                time = position.time,
                satellitesInView = position.sats_in_view,
                groundSpeed = position.ground_speed ?: 0,
                groundTrack = position.ground_track ?: 0,
                precisionBits = position.precision_bits,
            )
            .takeIf { latitude != 0.0 || longitude != 0.0 },
        snr = snr,
        rssi = rssi,
        lastHeard = lastHeard,
        deviceMetrics =
        org.meshtastic.core.model.DeviceMetrics(
            batteryLevel = deviceMetrics.battery_level ?: 0,
            voltage = deviceMetrics.voltage ?: 0f,
            channelUtilization = deviceMetrics.channel_utilization ?: 0f,
            airUtilTx = deviceMetrics.air_util_tx ?: 0f,
            uptimeSeconds = deviceMetrics.uptime_seconds ?: 0,
        ),
        channel = channel,
        environmentMetrics = org.meshtastic.core.model.EnvironmentMetrics.fromTelemetryProto(environmentMetrics, 0),
        hopsAway = hopsAway,
        nodeStatus = nodeStatus,
    )

    fun broadcastMessageStatus(p: DataPacket) = broadcastMessageStatus(p.id, p.status ?: MessageStatus.UNKNOWN)

    override fun broadcastMessageStatus(packetId: Int, status: MessageStatus) {
        if (packetId == 0) {
            Logger.d { "Ignoring anonymous packet status" }
        } else {
            // Do not log, contains PII possibly
            // MeshService.Logger.d { "Broadcasting message status $p" }
            val intent =
                Intent(ACTION_MESSAGE_STATUS).apply {
                    putExtra(EXTRA_PACKET_ID, packetId)
                    putExtra(EXTRA_STATUS, status as Parcelable)
                }
            explicitBroadcast(intent)
        }
    }

    /** Broadcast our current connection status */
    override fun broadcastConnection() {
        val connectionState = serviceRepository.connectionState.value
        // ATAK expects a String: "CONNECTED" or "DISCONNECTED"
        // It uses equalsIgnoreCase, but we'll use uppercase to be specific.
        val stateStr = connectionState.toString().uppercase(Locale.ROOT)

        val intent = Intent(ACTION_MESH_CONNECTED).apply { putExtra(EXTRA_CONNECTED, stateStr) }
        explicitBroadcast(intent)

        if (connectionState == ConnectionState.Disconnected) {
            explicitBroadcast(Intent(ACTION_MESH_DISCONNECTED))
        }

        // Restore legacy action for other consumers (e.g. ATAK plugins)
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
     *         because it implies we have assembled a valid node db.
     */
    private fun explicitBroadcast(intent: Intent) {
        context.sendBroadcast(
            intent,
        ) // We also do a regular (not explicit broadcast) so any context-registered receivers will work
        clientPackages.forEach {
            intent.setClassName(it.value, it.key)
            context.sendBroadcast(intent)
        }
    }
}

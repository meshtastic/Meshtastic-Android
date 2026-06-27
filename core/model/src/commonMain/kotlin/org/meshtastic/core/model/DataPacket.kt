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
package org.meshtastic.core.model

import kotlinx.serialization.Serializable
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.util.ByteStringSerializer
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Waypoint

enum class MessageStatus {
    UNKNOWN, // Not set for this message
    RECEIVED, // Came in from the mesh
    QUEUED, // Waiting to send to the mesh as soon as we connect to the device
    ENROUTE, // Delivered to the radio, but no ACK or NAK received
    DELIVERED, // We received an ack
    SFPP_ROUTING, // Message is being routed/buffered in the SFPP system
    SFPP_CONFIRMED, // Message is confirmed on the SFPP chain
    ERROR, // We received back a nak, message not delivered
}

/** A data class version of the protobuf MeshPacket + Data subpacket. */
@Serializable
data class DataPacket(
    var to: String? = NodeAddress.ID_BROADCAST,
    @Serializable(with = ByteStringSerializer::class) var bytes: ByteString?,
    // A port number for this packet
    var dataType: Int,
    var from: String? = NodeAddress.ID_LOCAL,
    var time: Long = nowMillis, // msecs since 1970
    var id: Int = 0, // 0 means unassigned
    var status: MessageStatus? = MessageStatus.UNKNOWN,
    var hopLimit: Int = 0,
    var channel: Int = 0, // channel index
    var wantAck: Boolean = true, // If true, the receiver should send an ack back
    var hopStart: Int = 0,
    var snr: Float = 0f,
    var rssi: Int = 0,
    var replyId: Int? = null, // If this is a reply to a previous message, this is the ID of that message
    var relayNode: Int? = null,
    var relays: Int = 0,
    var viaMqtt: Boolean = false, // True if this packet passed via MQTT somewhere along its path
    var emoji: Int = 0,
    @Serializable(with = ByteStringSerializer::class) var sfppHash: ByteString? = null,
    /** The transport mechanism this packet arrived over (see [MeshPacket.TransportMechanism]). */
    var transportMechanism: Int = 0,
    /** True when the radio verified this broadcast's XEdDSA signature ([MeshPacket.xeddsa_signed]). */
    var xeddsaSigned: Boolean = false,
) {

    /** If there was an error with this message, this string describes what was wrong. */
    var errorMessage: String? = null

    /** Syntactic sugar to make it easy to create text messages */
    constructor(
        to: String?,
        channel: Int,
        text: String,
        replyId: Int? = null,
    ) : this(
        to = to,
        bytes = text.encodeToByteArray().toByteString(),
        dataType = PortNum.TEXT_MESSAGE_APP.value,
        channel = channel,
        replyId = replyId,
    )

    /** If this is a text message, return the string, otherwise null */
    val text: String?
        get() =
            if (dataType == PortNum.TEXT_MESSAGE_APP.value) {
                bytes?.utf8()
            } else {
                null
            }

    val alert: String?
        get() =
            if (dataType == PortNum.ALERT_APP.value) {
                bytes?.utf8()
            } else {
                null
            }

    constructor(
        to: String?,
        channel: Int,
        waypoint: Waypoint,
    ) : this(
        to = to,
        bytes = Waypoint.ADAPTER.encode(waypoint).toByteString(),
        dataType = PortNum.WAYPOINT_APP.value,
        channel = channel,
    )

    val waypoint: Waypoint?
        get() =
            if (dataType == PortNum.WAYPOINT_APP.value) {
                try {
                    bytes?.let { Waypoint.ADAPTER.decode(it) }
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

    val hopsAway: Int
        get() = if (hopStart == 0 || (hopLimit > hopStart)) -1 else hopStart - hopLimit
}

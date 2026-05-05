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

import co.touchlab.kermit.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.CommonIgnoredOnParcel
import org.meshtastic.core.common.util.CommonParcel
import org.meshtastic.core.common.util.CommonParcelable
import org.meshtastic.core.common.util.CommonParcelize
import org.meshtastic.core.common.util.CommonTypeParceler
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.util.ByteStringParceler
import org.meshtastic.core.model.util.ByteStringSerializer
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Waypoint
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.fromDefaultId
import org.meshtastic.sdk.toDefaultId

@CommonParcelize
enum class MessageStatus : CommonParcelable {
    UNKNOWN, // Not set for this message
    RECEIVED, // Came in from the mesh
    QUEUED, // Waiting to send to the mesh as soon as we connect to the device
    ENROUTE, // Delivered to the radio, but no ACK or NAK received
    DELIVERED, // We received an ack
    SFPP_ROUTING, // Message is being routed/buffered in the SFPP system
    SFPP_CONFIRMED, // Message is confirmed on the SFPP chain
    ERROR, // We received back a nak, message not delivered
}

/** A parcelable version of the protobuf MeshPacket + Data subpacket. */
@Serializable
@CommonParcelize
data class DataPacket(
    @Serializable(with = NodeNumSerializer::class)
    var to: Int = BROADCAST,
    @Serializable(with = ByteStringSerializer::class)
    @CommonTypeParceler<ByteString?, ByteStringParceler>
    var bytes: ByteString?,
    // A port number for this packet
    var dataType: Int,
    @Serializable(with = NodeNumSerializer::class)
    var from: Int = LOCAL,
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
    @Serializable(with = ByteStringSerializer::class)
    @CommonTypeParceler<ByteString?, ByteStringParceler>
    var sfppHash: ByteString? = null,
    /** The transport mechanism this packet arrived over (see [MeshPacket.TransportMechanism]). */
    var transportMechanism: Int = 0,
) : CommonParcelable {

    fun readFromParcel(parcel: CommonParcel) {
        to = parcel.readInt()
        bytes = ByteStringParceler.create(parcel)
        dataType = parcel.readInt()
        from = parcel.readInt()
        time = parcel.readLong()
        id = parcel.readInt()

        // MessageStatus is a known Parcelable type (enum), so Parcelize writes it optimized:
        // 1. Presence flag (Int: 1 or 0)
        // 2. Content (Enum Name as String)
        status =
            if (parcel.readInt() != 0) {
                val name = parcel.readString()
                try {
                    if (name != null) MessageStatus.valueOf(name) else MessageStatus.UNKNOWN
                } catch (e: IllegalArgumentException) {
                    Logger.w(e) { "Unknown MessageStatus: $name" }
                    MessageStatus.UNKNOWN
                }
            } else {
                null
            }

        hopLimit = parcel.readInt()
        channel = parcel.readInt()
        wantAck = (parcel.readInt() != 0)
        hopStart = parcel.readInt()
        snr = parcel.readFloat()
        rssi = parcel.readInt()
        replyId = if (parcel.readInt() == 0) null else parcel.readInt()
        relayNode = if (parcel.readInt() == 0) null else parcel.readInt()
        relays = parcel.readInt()
        viaMqtt = (parcel.readInt() != 0)
        emoji = parcel.readInt()
        sfppHash = ByteStringParceler.create(parcel)
        transportMechanism = parcel.readInt()
    }

    /** If there was an error with this message, this string describes what was wrong. */
    @CommonIgnoredOnParcel var errorMessage: String? = null

    /** Syntactic sugar to make it easy to create text messages */
    constructor(
        to: Int,
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
        to: Int,
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

    companion object {
        const val BROADCAST: Int = 0xffffffff.toInt()
        const val LOCAL: Int = 0

        // Public-key cryptography (PKC) channel index
        const val PKC_CHANNEL_INDEX = 8

        /** Format a node number as the default display ID ("!aabbccdd"). */
        fun nodeNumToDefaultId(n: Int): String = NodeId(n).toDefaultId()

        fun nodeNumToId(n: Int): String = when (n) {
            BROADCAST -> "^all"
            LOCAL -> "^local"
            else -> nodeNumToDefaultId(n)
        }

        fun parseNodeNum(id: String): Int {
            val normalized = id.trim()
            return when {
                normalized.equals("^all", ignoreCase = true) -> BROADCAST
                normalized.equals("^local", ignoreCase = true) -> LOCAL
                else -> NodeId.fromDefaultId(normalized)?.raw
                    ?: NodeId.fromDefaultId("!$normalized")?.raw
                    ?: runCatching { normalized.toLong(16).toInt() }.getOrNull()
                    ?: throw SerializationException("Unsupported node id: $id")
            }
        }
    }
}

private object NodeNumSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NodeNum", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeString(DataPacket.nodeNumToId(value))
    }

    override fun deserialize(decoder: Decoder): Int {
        if (decoder is JsonDecoder) {
            val primitive = decoder.decodeJsonElement().jsonPrimitive
            primitive.intOrNull?.let { return it }
            return DataPacket.parseNodeNum(primitive.content)
        }
        return DataPacket.parseNodeNum(decoder.decodeString())
    }
}

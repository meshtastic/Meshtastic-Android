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
package org.meshtastic.core.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.serialization.Serializable
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.util.ByteStringParceler
import org.meshtastic.core.model.util.ByteStringSerializer
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Waypoint

/** Generic [Parcel.readParcelable] Android 13 compatibility extension. */
private inline fun <reified T : Parcelable> Parcel.readParcelableCompat(loader: ClassLoader?): T? =
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        readParcelable(loader)
    } else {
        readParcelable(loader, T::class.java)
    }

@Parcelize
enum class MessageStatus : Parcelable {
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
@Parcelize
data class DataPacket(
    var to: String? = ID_BROADCAST, // a nodeID string, or ID_BROADCAST for broadcast
    @Serializable(with = ByteStringSerializer::class)
    @TypeParceler<ByteString?, ByteStringParceler>
    var bytes: ByteString?,
    // A port number for this packet (formerly called DataType, see portnums.proto for new usage instructions)
    var dataType: Int,
    var from: String? = ID_LOCAL, // a nodeID string, or ID_LOCAL for localhost
    var time: Long = System.currentTimeMillis(), // msecs since 1970
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
    var retryCount: Int = 0, // Number of automatic retry attempts
    var emoji: Int = 0,
    @Serializable(with = ByteStringSerializer::class)
    @TypeParceler<ByteString?, ByteStringParceler>
    var sfppHash: ByteString? = null,
) : Parcelable {

    /** If there was an error with this message, this string describes what was wrong. */
    @IgnoredOnParcel var errorMessage: String? = null

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
        replyId = replyId ?: 0,
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
        bytes = waypoint.encode().toByteString(),
        dataType = PortNum.WAYPOINT_APP.value,
        channel = channel,
    )

    val waypoint: Waypoint?
        get() =
            if (dataType == PortNum.WAYPOINT_APP.value) {
                Waypoint.ADAPTER.decode(bytes?.toByteArray() ?: ByteArray(0))
            } else {
                null
            }

    val hopsAway: Int
        get() = if (hopStart == 0 || hopLimit > hopStart) -1 else hopStart - hopLimit

    // Update our object from our parcel (used for inout parameters)
    fun readFromParcel(parcel: Parcel) {
        to = parcel.readString()
        bytes = parcel.createByteArray()?.toByteString()
        dataType = parcel.readInt()
        from = parcel.readString()
        time = parcel.readLong()
        id = parcel.readInt()
        status = parcel.readParcelableCompat(MessageStatus::class.java.classLoader)
        hopLimit = parcel.readInt()
        channel = parcel.readInt()
        wantAck = parcel.readInt() != 0
        hopStart = parcel.readInt()
        snr = parcel.readFloat()
        rssi = parcel.readInt()
        // @Parcelize writes a presence flag (1) before the value for nullable primitives
        replyId = if (parcel.readInt() != 0) parcel.readInt() else null
        relayNode = if (parcel.readInt() != 0) parcel.readInt() else null
        relays = parcel.readInt()
        viaMqtt = parcel.readInt() != 0
        retryCount = parcel.readInt()
        emoji = parcel.readInt()
        sfppHash = parcel.createByteArray()?.toByteString()
    }

    companion object {
        // Special node IDs that can be used for sending messages

        /** the Node ID for broadcast destinations */
        const val ID_BROADCAST = "^all"

        /** The Node ID for the local node - used for from when sender doesn't know our local node ID */
        const val ID_LOCAL = "^local"

        // special broadcast address
        const val NODENUM_BROADCAST = (0xffffffff).toInt()

        // Public-key cryptography (PKC) channel index
        const val PKC_CHANNEL_INDEX = 8

        fun nodeNumToDefaultId(n: Int): String = "!%08x".format(n)

        @Suppress("MagicNumber")
        fun idToDefaultNodeNum(id: String?): Int? = runCatching { id?.toLong(16)?.toInt() }.getOrNull()
    }
}

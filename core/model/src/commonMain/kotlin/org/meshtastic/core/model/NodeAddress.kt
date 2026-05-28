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

import org.meshtastic.core.common.util.formatString
import kotlin.jvm.JvmInline

/**
 * Type-safe representation of a mesh node address.
 *
 * Replaces stringly-typed node addressing (`"^all"`, `"^local"`, `"!hexid"`) with exhaustive sealed dispatch, enabling
 * compile-time verification of address handling.
 */
sealed class NodeAddress {
    /** Broadcast to all nodes in the mesh. */
    data object Broadcast : NodeAddress()

    /** The local node (used as `from` when the sender's ID is unknown). */
    data object Local : NodeAddress()

    /** Address by numeric node number (the canonical mesh-level identifier). */
    data class ByNum(val num: Int) : NodeAddress()

    /** Address by hex string ID (e.g. `"!a1b2c3d4"`). */
    data class ById(val id: String) : NodeAddress()

    /** Convert back to the legacy string representation used in [DataPacket]. */
    fun toIdString(): String = when (this) {
        Broadcast -> ID_BROADCAST
        Local -> ID_LOCAL
        is ByNum -> numToDefaultId(num)
        is ById -> id
    }

    /** Build a [ContactKey] for this address on the given [channel]. */
    fun toContactKey(channel: Int): ContactKey = ContactKey("$channel${toIdString()}")

    companion object {
        /** The broadcast address string `"^all"`. */
        const val ID_BROADCAST = "^all"

        /** The local node address string `"^local"`. */
        const val ID_LOCAL = "^local"

        /** The broadcast node number (`0xFFFFFFFF`). */
        @Suppress("MagicNumber")
        const val NODENUM_BROADCAST = (0xffffffff).toInt()

        /** Public-key cryptography (PKC) channel index. */
        const val PKC_CHANNEL_INDEX = 8

        private const val NODE_ID_PREFIX = "!"
        private const val HEX_RADIX = 16

        /** Parse a legacy string address into a typed [NodeAddress]. */
        fun fromString(id: String?): NodeAddress = when {
            id == null || id == ID_BROADCAST -> Broadcast
            id == ID_LOCAL -> Local
            id.startsWith(NODE_ID_PREFIX) -> idToNum(id)?.let(::ByNum) ?: ById(id)
            else -> ById(id)
        }

        /** Convert a node number to its canonical hex string ID (e.g. `"!a1b2c3d4"`). */
        fun numToDefaultId(n: Int): String = formatString("!%08x", n)

        /**
         * Parse a hex node ID string (with or without `!` prefix) to its integer value, or null if the input is not a
         * valid 32-bit hex value. Values larger than `0xFFFFFFFF` return null rather than silently truncating, so a
         * malformed `!100000000` won't be misclassified as node `0`.
         */
        @Suppress("MagicNumber")
        fun idToNum(id: String?): Int? =
            id?.removePrefix(NODE_ID_PREFIX)?.toLongOrNull(HEX_RADIX)?.takeIf { it in 0L..0xFFFFFFFFL }?.toInt()
    }
}

/**
 * Type-safe wrapper for contact key strings (channel index + node address).
 *
 * Contact keys are persisted as strings in the format `"<channel><nodeId>"` (e.g. `"0^all"`, `"1!a1b2c3d4"`).
 */
@JvmInline
value class ContactKey(val value: String) {
    /** The channel index (first character). Returns 0 if the key is empty or the first char is not a digit. */
    val channel: Int
        get() = value.firstOrNull()?.takeIf { it.isDigit() }?.digitToInt() ?: 0

    /** The node address portion (everything after the channel digit). Empty if the key is empty. */
    val addressString: String
        get() = if (value.isEmpty()) "" else value.substring(1)

    /** Parsed [NodeAddress] for the contact. */
    val address: NodeAddress
        get() = NodeAddress.fromString(addressString)

    companion object {
        /** Create a broadcast contact key for the given channel. */
        fun broadcast(channel: Int = 0): ContactKey = NodeAddress.Broadcast.toContactKey(channel)
    }
}

/** Type-safe interpretation of [DataPacket.to]. */
val DataPacket.destination: NodeAddress
    get() = NodeAddress.fromString(to)

/** Type-safe interpretation of [DataPacket.from]. */
val DataPacket.source: NodeAddress
    get() = NodeAddress.fromString(from)

/** Checks whether this packet originated from the local device. */
fun DataPacket.isFromLocal(myNodeNum: Int? = null): Boolean {
    val src = source
    return src is NodeAddress.Local || (myNodeNum != null && src is NodeAddress.ByNum && src.num == myNodeNum)
}

/** Checks whether this packet is addressed to the broadcast channel. */
val DataPacket.isBroadcast: Boolean
    get() = destination is NodeAddress.Broadcast

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
package org.meshtastic.core.repository

/**
 * An admin request this app sent and has not seen answered.
 *
 * @property requestId The packet id of the request.
 * @property destNum The node the request was addressed to.
 * @property adminChannelIndex The index of the legacy `admin` channel when the request was sent, or null when there was
 *   none.
 */
data class OutstandingAdminRequest(val requestId: Int, val destNum: Int, val adminChannelIndex: Int?) {
    /** True when a response from [from] belongs to this request and, for a remote node, is authenticated. */
    fun acceptsResponseFrom(from: Int, myNodeNum: Int?, pkiEncrypted: Boolean, channel: Int): Boolean =
        from == destNum && (destNum == myNodeNum || pkiEncrypted || isOnAdminChannel(channel))

    private fun isOnAdminChannel(channel: Int): Boolean = adminChannelIndex != null && channel == adminChannelIndex
}

/**
 * Admin requests this app has sent and not yet seen answered. An inbound admin response is authoritative only when it
 * answers one of these from the node it was addressed to: the firmware forwards every decoded `ADMIN_APP` packet
 * addressed to the node, and `from` is an unauthenticated header field.
 */
interface AdminRequestRegistry {
    /** Records an admin request about to be sent to [destNum]. */
    fun register(requestId: Int, destNum: Int, adminChannelIndex: Int?)

    /** Returns the outstanding request with [requestId] without forgetting it, or null. */
    fun find(requestId: Int): OutstandingAdminRequest?

    /** Returns true and forgets [requestId] when it names an outstanding request; false otherwise. */
    fun consume(requestId: Int): Boolean

    /** Forgets every outstanding request. Call on radio teardown. */
    fun clearAll()
}

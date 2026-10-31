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
 * Packet ids of the admin requests this app has sent and not yet seen answered. An inbound admin response is
 * authoritative only when its `request_id` consumes one of these: the firmware forwards every decoded `ADMIN_APP`
 * packet addressed to the node, and `from` is an unauthenticated header field.
 */
interface AdminRequestRegistry {
    /** Records the packet id of an admin request about to be sent. */
    fun register(requestId: Int)

    /** Returns true and forgets [requestId] when it names an outstanding request; false otherwise. */
    fun consume(requestId: Int): Boolean

    /** Forgets every outstanding request. Call on radio teardown. */
    fun clearAll()
}

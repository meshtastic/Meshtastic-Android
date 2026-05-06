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

/**
 * Domain-level exception for admin (configuration) operations that fail for expected reasons.
 *
 * These failures are part of normal mesh operation — a remote node may be unreachable, the
 * session key may have expired, or the request may time out. They are NOT thrown for catastrophic
 * failures (transport gone, engine torn down) which throw standard exceptions.
 */
sealed class AdminException(message: String) : Exception(message) {

    /** The admin request timed out waiting for a device response. */
    class Timeout : AdminException("Request timed out")

    /** Client is not authorized to perform this operation on the target node. */
    class Unauthorized : AdminException("Not authorized")

    /** The destination node is unreachable (no route, NAK, or max retransmit). */
    class NodeUnreachable : AdminException("Node unreachable")

    /** Session key expired or was never established; a retry may succeed after re-seeding. */
    class SessionKeyExpired : AdminException("Session key expired")

    /** Device reported a routing error not covered by the other subtypes. */
    class RoutingError(val errorName: String) : AdminException("Routing error: $errorName")
}

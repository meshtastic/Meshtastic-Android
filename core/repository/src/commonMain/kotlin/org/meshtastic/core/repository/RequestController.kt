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

import org.meshtastic.core.model.Position

/**
 * Mesh request operations — position, traceroute, telemetry, user info, and metadata queries.
 *
 * These are "pull" operations that request data from remote nodes. When the SDK is adopted, implementations delegate to
 * `RadioClient.telemetry` and `RadioClient.routing` sub-APIs.
 *
 * @see RadioController which extends this interface for backward compatibility
 */
interface RequestController {

    /** Requests device metadata from a remote node. */
    suspend fun refreshMetadata(destNum: Int)

    /** Requests the current GPS position from a remote node. */
    suspend fun requestPosition(destNum: Int, currentPosition: Position)

    /** Requests detailed user info from a remote node. */
    suspend fun requestUserInfo(destNum: Int)

    /** Initiates a traceroute request to a remote node. */
    suspend fun requestTraceroute(requestId: Int, destNum: Int)

    /** Requests telemetry data from a remote node. */
    suspend fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int)

    /** Requests neighbor information (detected nodes) from a remote node. */
    suspend fun requestNeighborInfo(requestId: Int, destNum: Int)
}

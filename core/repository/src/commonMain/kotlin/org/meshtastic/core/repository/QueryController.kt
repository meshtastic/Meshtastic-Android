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
import org.meshtastic.core.model.TelemetryType

/**
 * Mesh query operations — position, traceroute, telemetry, user info, and metadata.
 *
 * These are "pull" operations that request data from remote nodes through the session's SDK client. Each returns once
 * the radio admitted the request; its [NodeRequest] reports whether the mesh then failed it. The reply itself reaches
 * Room through the session's receive projection.
 *
 * @throws PacketQueueRejectedException from each request when no radio session can take it.
 * @see RadioController
 */
interface QueryController {

    /** Requests device metadata from a node. */
    suspend fun refreshMetadata(destNum: Int): NodeRequest

    /** Requests the current position of a remote node, sharing ours when the user allows it. */
    suspend fun requestPosition(destNum: Int, currentPosition: Position): NodeRequest

    /** Requests user info from a remote node, carrying ours. */
    suspend fun requestUserInfo(destNum: Int): NodeRequest

    /** Initiates a traceroute request to a remote node. */
    suspend fun requestTraceroute(destNum: Int): NodeRequest

    /** Requests one kind of telemetry from a node. */
    suspend fun requestTelemetry(destNum: Int, type: TelemetryType): NodeRequest

    /** Requests neighbor information (detected nodes) from a node. */
    suspend fun requestNeighborInfo(destNum: Int): NodeRequest
}

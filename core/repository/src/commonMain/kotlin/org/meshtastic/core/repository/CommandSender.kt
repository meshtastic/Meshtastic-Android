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
 * The node requests the SDK cannot yet send as firmware expects, built here and queued through [PacketHandler].
 *
 * The SDK's `RoutingApi.traceRoute` sends a `ROUTING_APP` route request that firmware's `TraceRouteModule` never sees,
 * its `requestNeighborInfo` sends a remote request with hop limit 0 and no `want_ack` on channel 0, neither exposes the
 * request ID the app logs and times the reply by, and `TelemetryApi.requestLocalStats` reaches only the local node.
 *
 * @throws PacketQueueRejectedException from each request when the radio session refuses admission.
 * @throws LocalNodeUnavailableException from each request before the local node identity is known.
 */
interface CommandSender {
    /** Requests a traceroute to [destNum] and records its start for the reply's duration. */
    suspend fun requestTraceroute(destNum: Int): NodeRequest

    /** Requests [destNum]'s neighbor table and records its start for the reply's duration. */
    suspend fun requestNeighborInfo(destNum: Int): NodeRequest

    /** Requests the local stats of [destNum], which may be a remote node. */
    suspend fun requestLocalStats(destNum: Int): NodeRequest
}

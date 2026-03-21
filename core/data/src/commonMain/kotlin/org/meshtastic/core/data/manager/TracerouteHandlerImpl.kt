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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.fullRouteDiscovery
import org.meshtastic.core.model.getFullTracerouteResponse
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.repository.TracerouteSnapshotRepository
import org.meshtastic.proto.MeshPacket

@Single
class TracerouteHandlerImpl(
    private val nodeManager: NodeManager,
    private val serviceRepository: ServiceRepository,
    private val tracerouteSnapshotRepository: TracerouteSnapshotRepository,
    private val nodeRepository: NodeRepository,
) : TracerouteHandler {
    private var scope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val startTimes = mutableMapOf<Int, Long>()

    override fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    override fun recordStartTime(requestId: Int) {
        startTimes[requestId] = nowMillis
    }

    override fun handleTraceroute(packet: MeshPacket, logUuid: String?, logInsertJob: kotlinx.coroutines.Job?) {
        val full =
            packet.getFullTracerouteResponse(
                getUser = { num ->
                    nodeManager.nodeDBbyNodeNum[num]?.let { node: Node ->
                        "${node.user.long_name} (${node.user.short_name})"
                    } ?: "Unknown" // We don't have strings in core:data yet, but we can fix this later
                },
                headerTowards = "Route towards destination:",
                headerBack = "Route back to us:",
            ) ?: return

        val requestId = packet.decoded?.request_id ?: 0
        if (logUuid != null) {
            scope.handledLaunch {
                logInsertJob?.join()
                val routeDiscovery = packet.fullRouteDiscovery
                val forwardRoute = routeDiscovery?.route.orEmpty()
                val returnRoute = routeDiscovery?.route_back.orEmpty()
                val routeNodeNums = (forwardRoute + returnRoute).distinct()
                val nodeDbByNum = nodeRepository.nodeDBbyNum.value
                val snapshotPositions =
                    routeNodeNums.mapNotNull { num -> nodeDbByNum[num]?.validPosition?.let { num to it } }.toMap()
                tracerouteSnapshotRepository.upsertSnapshotPositions(logUuid, requestId, snapshotPositions)
            }
        }

        val start = startTimes.remove(requestId)
        val responseText =
            if (start != null) {
                val elapsedMs = nowMillis - start
                val seconds = elapsedMs / MILLIS_PER_SECOND
                Logger.i { "Traceroute $requestId complete in $seconds s" }
                val durationText = "Duration: ${NumberFormatter.format(seconds, 1)} s"
                "$full\n\n$durationText"
            } else {
                full
            }

        val routeDiscovery = packet.fullRouteDiscovery
        val destination = routeDiscovery?.route?.firstOrNull() ?: routeDiscovery?.route_back?.lastOrNull() ?: 0

        serviceRepository.setTracerouteResponse(
            TracerouteResponse(
                message = responseText,
                destinationNodeNum = destination,
                requestId = requestId,
                forwardRoute = routeDiscovery?.route.orEmpty(),
                returnRoute = routeDiscovery?.route_back.orEmpty(),
                logUuid = logUuid,
            ),
        )
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000.0
    }
}

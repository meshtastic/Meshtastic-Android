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
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.fullRouteDiscovery
import org.meshtastic.core.model.getTracerouteResponse
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.repository.TracerouteSnapshotRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.traceroute_route_back_to_us
import org.meshtastic.core.resources.traceroute_route_towards_dest
import org.meshtastic.proto.MeshPacket

@Single
class TracerouteHandlerImpl(
    private val serviceRepository: ServiceRepository,
    private val tracerouteSnapshotRepository: TracerouteSnapshotRepository,
    private val nodeRepository: NodeRepository,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : TracerouteHandler {

    private val startTimes = atomic(persistentMapOf<Int, Long>())

    override fun recordStartTime(requestId: Int) {
        startTimes.update { it.put(requestId, nowMillis) }
    }

    override fun handleTraceroute(packet: MeshPacket, logUuid: String?, logInsertJob: Job?) {
        // Decode the route discovery once — avoids triple protobuf decode
        val routeDiscovery = packet.fullRouteDiscovery ?: return
        val forwardRoute = routeDiscovery.route
        val returnRoute = routeDiscovery.route_back

        // Require both directions for a "full" traceroute response
        if (forwardRoute.isEmpty() || returnRoute.isEmpty()) return

        scope.handledLaunch {
            val full =
                routeDiscovery.getTracerouteResponse(
                    getUser = { num ->
                        val user = nodeRepository.getUser(num)
                        "${user.long_name} (${user.short_name})"
                    },
                    headerTowards = getStringSuspend(Res.string.traceroute_route_towards_dest),
                    headerBack = getStringSuspend(Res.string.traceroute_route_back_to_us),
                )

            val requestId = packet.decoded?.request_id ?: 0

            if (logUuid != null) {
                logInsertJob?.join()
                val routeNodeNums = (forwardRoute + returnRoute).distinct()
                val nodeDbByNum = nodeRepository.nodeDBbyNum.value
                val snapshotPositions =
                    routeNodeNums.mapNotNull { num -> nodeDbByNum[num]?.validPosition?.let { num to it } }.toMap()
                tracerouteSnapshotRepository.upsertSnapshotPositions(logUuid, requestId, snapshotPositions)
            }

            val start = startTimes.value[requestId]
            startTimes.update { it.remove(requestId) }
            val responseText =
                if (start != null) {
                    val elapsedMs = nowMillis - start
                    val seconds = elapsedMs / MILLIS_PER_SECOND
                    Logger.i { "Traceroute $requestId complete in $seconds s" }
                    "$full\n\nDuration: ${NumberFormatter.format(seconds, 1)} s"
                } else {
                    full
                }

            val destination = forwardRoute.firstOrNull() ?: returnRoute.lastOrNull() ?: 0

            serviceRepository.setTracerouteResponse(
                TracerouteResponse(
                    message = responseText,
                    destinationNodeNum = destination,
                    requestId = requestId,
                    forwardRoute = forwardRoute,
                    returnRoute = returnRoute,
                    logUuid = logUuid,
                ),
            )
        }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000.0
    }
}

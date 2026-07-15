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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.model.fullRouteDiscovery
import org.meshtastic.core.model.getTracerouteResponse
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.repository.TracerouteSnapshotRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.traceroute_route_back_to_us
import org.meshtastic.core.resources.traceroute_route_towards_dest
import org.meshtastic.proto.MeshPacket

@Single
class TracerouteHandlerImpl(
    private val serviceStateWriter: ServiceStateWriter,
    private val tracerouteSnapshotRepository: TracerouteSnapshotRepository,
    private val nodeRepository: NodeRepository,
    private val radioInterfaceService: RadioInterfaceService,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : TracerouteHandler {

    private val requestTimer = RequestTimer()

    override fun recordStartTime(requestId: Int) = requestTimer.start(requestId)

    override fun handleTraceroute(
        packet: MeshPacket,
        logUuid: String?,
        logInsertJob: Job?,
        session: RadioSessionContext,
    ) {
        // Decode the route discovery once — avoids triple protobuf decode.
        val routeDiscovery = packet.fullRouteDiscovery ?: return
        val forwardRoute = routeDiscovery.route
        val returnRoute = routeDiscovery.route_back
        if (forwardRoute.isEmpty() || returnRoute.isEmpty()) return

        radioInterfaceService.launchSessionWork(
            scope = scope,
            session = session,
            onRejected = { Logger.d { "Dropped traceroute work from a retired transport session" } },
        ) {
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

            val responseText = requestTimer.appendDuration(requestId, full, "Traceroute")
            val destination = forwardRoute.firstOrNull() ?: returnRoute.lastOrNull() ?: 0
            serviceStateWriter.setTracerouteResponse(
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
}

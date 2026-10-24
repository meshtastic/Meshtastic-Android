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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.model.fullRouteDiscovery
import org.meshtastic.core.model.getTracerouteResponse
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.core.model.tracerouteHopName
import org.meshtastic.core.repository.MeshLogRepository
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
import org.meshtastic.core.resources.traceroute_unknown_hop
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.RouteDiscovery

@Single
class TracerouteHandlerImpl(
    private val serviceStateWriter: ServiceStateWriter,
    private val tracerouteSnapshotRepository: TracerouteSnapshotRepository,
    private val nodeRepository: NodeRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val meshLogRepository: Lazy<MeshLogRepository>,
    private val scope: ServiceScope,
) : TracerouteHandler {

    private val requestTimer = RequestTimer()

    /** Renders the alert text. Replaced in unit tests, where Compose resources cannot load. */
    internal var renderResponse: suspend (RouteDiscovery) -> String = { routeDiscovery ->
        val unknownHopLabel = getStringSuspend(Res.string.traceroute_unknown_hop)
        routeDiscovery.getTracerouteResponse(
            getUser = { num ->
                tracerouteHopName(
                    num,
                    nodeRepository.getUser(num),
                    unknownHopLabel,
                    nodeRepository.nodeDBbyNum.value[num]?.displayName.orEmpty(),
                )
            },
            headerTowards = getStringSuspend(Res.string.traceroute_route_towards_dest),
            headerBack = getStringSuspend(Res.string.traceroute_route_back_to_us),
        )
    }

    override fun recordStartTime(requestId: Int, destination: Int) = requestTimer.start(requestId, destination)

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
            val requestId = packet.decoded?.request_id ?: 0

            if (logUuid != null) {
                logInsertJob?.join()
                val routeNodeNums = (forwardRoute + returnRoute).distinct()
                val nodeDbByNum = nodeRepository.nodeDBbyNum.value
                val snapshotPositions =
                    routeNodeNums.mapNotNull { num -> nodeDbByNum[num]?.validPosition?.let { num to it } }.toMap()
                tracerouteSnapshotRepository.upsertSnapshotPositions(logUuid, requestId, snapshotPositions)
            }

            // A response we did not ask for (a trace started on the radio's screen, another client) is recorded in
            // history as observed and raises no alert.
            if (!isOurRequest(requestId, packet.from)) {
                Logger.d { "Traceroute response $requestId was not requested by this app; recorded as observed" }
                return@launchSessionWork
            }

            val responseText =
                requestTimer.appendDuration(requestId, renderResponse(routeDiscovery), "Traceroute", packet.from)
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

    /**
     * The in-memory timer covers requests sent since this process started; the request's MeshLog row covers one sent
     * before a restart. Either way the response must come from the node the request was sent to.
     */
    private suspend fun isOurRequest(requestId: Int, responder: Int): Boolean = requestId != 0 &&
        (
            requestTimer.has(requestId, responder) ||
                meshLogRepository.value.getRequestLogs(responder, PortNum.TRACEROUTE_APP).first().any {
                    it.fromRadio.packet?.id == requestId
                }
            )
}

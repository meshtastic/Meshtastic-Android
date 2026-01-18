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
package com.geeksville.mesh.service

import co.touchlab.kermit.Logger
import com.geeksville.mesh.concurrent.handledLaunch
import com.meshtastic.core.strings.getString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.TracerouteSnapshotRepository
import org.meshtastic.core.model.fullRouteDiscovery
import org.meshtastic.core.model.getFullTracerouteResponse
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.service.TracerouteResponse
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.traceroute_duration
import org.meshtastic.core.strings.traceroute_route_back_to_us
import org.meshtastic.core.strings.traceroute_route_towards_dest
import org.meshtastic.core.strings.unknown_username
import org.meshtastic.proto.MeshPacket
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshTracerouteHandler
@Inject
constructor(
    private val nodeManager: MeshNodeManager,
    private val serviceRepository: ServiceRepository,
    private val tracerouteSnapshotRepository: TracerouteSnapshotRepository,
    private val nodeRepository: NodeRepository,
    private val commandSender: MeshCommandSender,
) {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    fun handleTraceroute(packet: MeshPacket, logUuid: String?, logInsertJob: kotlinx.coroutines.Job?) {
        val full =
            packet.getFullTracerouteResponse(
                getUser = { num ->
                    nodeManager.nodeDBbyNodeNum[num]?.let { "${it.longName} (${it.shortName})" }
                        ?: getString(Res.string.unknown_username)
                },
                headerTowards = getString(Res.string.traceroute_route_towards_dest),
                headerBack = getString(Res.string.traceroute_route_back_to_us),
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

        val start = commandSender.tracerouteStartTimes.remove(requestId)
        val responseText =
            if (start != null) {
                val elapsedMs = System.currentTimeMillis() - start
                val seconds = elapsedMs / MILLISECONDS_IN_SECOND
                Logger.i { "Traceroute $requestId complete in $seconds s" }
                val durationText = getString(Res.string.traceroute_duration, "%.1f".format(Locale.US, seconds))
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
        private const val MILLISECONDS_IN_SECOND = 1000.0
    }
}

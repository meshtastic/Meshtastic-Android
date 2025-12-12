/*
 * Copyright (c) 2025 Meshtastic LLC
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

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.traceroute_endpoint_missing
import org.meshtastic.core.strings.traceroute_map_no_data
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.MeshProtos.RouteDiscovery
import org.meshtastic.proto.Portnums

val MeshProtos.MeshPacket.fullRouteDiscovery: RouteDiscovery?
    get() =
        with(decoded) {
            if (hasDecoded() && !wantResponse && portnum == Portnums.PortNum.TRACEROUTE_APP) {
                runCatching { RouteDiscovery.parseFrom(payload).toBuilder() }
                    .getOrNull()
                    ?.apply {
                        val destinationId = dest.takeIf { it != 0 } ?: this@fullRouteDiscovery.to
                        val sourceId = source.takeIf { it != 0 } ?: this@fullRouteDiscovery.from
                        val fullRoute = listOf(destinationId) + routeList + sourceId
                        clearRoute()
                        addAllRoute(fullRoute)

                        val fullRouteBack = listOf(sourceId) + routeBackList + destinationId
                        clearRouteBack()
                        if (hopStart > 0 && snrBackCount > 0) { // otherwise back route is invalid
                            addAllRouteBack(fullRouteBack)
                        }
                    }
                    ?.build()
            } else {
                null
            }
        }

@Suppress("MagicNumber")
private fun formatTraceroutePath(nodesList: List<String>, snrList: List<Int>): String {
    // nodesList should include both origin and destination nodes
    // origin will not have an SNR value, but destination should
    val snrStr =
        if (snrList.size == nodesList.size - 1) {
            snrList
        } else {
            // use unknown SNR for entire route if snrList has invalid size
            List(nodesList.size - 1) { -128 }
        }
            .map { snr ->
                val str = if (snr == -128) "?" else "${snr / 4f}"
                "⇊ $str dB"
            }

    return nodesList
        .map { userName -> "■ $userName" }
        .flatMapIndexed { i, nodeStr -> if (i == 0) listOf(nodeStr) else listOf(snrStr[i - 1], nodeStr) }
        .joinToString("\n")
}

private fun RouteDiscovery.getTracerouteResponse(getUser: (nodeNum: Int) -> String): String = buildString {
    if (routeList.isNotEmpty()) {
        append("Route traced toward destination:\n\n")
        append(formatTraceroutePath(routeList.map(getUser), snrTowardsList))
    }
    if (routeBackList.isNotEmpty()) {
        append("\n\n")
        append("Route traced back to us:\n\n")
        append(formatTraceroutePath(routeBackList.map(getUser), snrBackList))
    }
}

fun MeshProtos.MeshPacket.getTracerouteResponse(getUser: (nodeNum: Int) -> String): String? =
    fullRouteDiscovery?.getTracerouteResponse(getUser)

/** Returns a traceroute response string only when the result is complete (both directions). */
fun MeshProtos.MeshPacket.getFullTracerouteResponse(getUser: (nodeNum: Int) -> String): String? = fullRouteDiscovery
    ?.takeIf { it.routeList.isNotEmpty() && it.routeBackList.isNotEmpty() }
    ?.getTracerouteResponse(getUser)

enum class TracerouteMapAvailability {
    Ok,
    MissingEndpoints,
    NoMappableNodes,
}

fun evaluateTracerouteMapAvailability(
    forwardRoute: List<Int>,
    returnRoute: List<Int>,
    positionedNodeNums: Set<Int>,
): TracerouteMapAvailability {
    if (forwardRoute.isEmpty() && returnRoute.isEmpty()) return TracerouteMapAvailability.NoMappableNodes
    val endpoints =
        listOfNotNull(
            forwardRoute.firstOrNull(),
            forwardRoute.lastOrNull(),
            returnRoute.firstOrNull(),
            returnRoute.lastOrNull(),
        )
            .distinct()
    val missingEndpoint = endpoints.any { !positionedNodeNums.contains(it) }
    if (missingEndpoint) return TracerouteMapAvailability.MissingEndpoints
    val relatedNodeNums = (forwardRoute + returnRoute).toSet()
    val hasAnyMappable = relatedNodeNums.any { positionedNodeNums.contains(it) }
    return if (hasAnyMappable) TracerouteMapAvailability.Ok else TracerouteMapAvailability.NoMappableNodes
}

fun TracerouteMapAvailability.toMessageRes(): StringResource? = when (this) {
    TracerouteMapAvailability.Ok -> null
    TracerouteMapAvailability.MissingEndpoints -> Res.string.traceroute_endpoint_missing
    TracerouteMapAvailability.NoMappableNodes -> Res.string.traceroute_map_no_data
}

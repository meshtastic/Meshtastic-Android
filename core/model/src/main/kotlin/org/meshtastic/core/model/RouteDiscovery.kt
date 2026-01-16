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
package org.meshtastic.core.model

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.traceroute_endpoint_missing
import org.meshtastic.core.strings.traceroute_map_no_data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.RouteDiscovery

val MeshPacket.fullRouteDiscovery: RouteDiscovery?
    get() {
        val dec = decoded ?: return null
        if (dec.want_response != true && dec.portnum == PortNum.TRACEROUTE_APP) {
            return runCatching { RouteDiscovery.ADAPTER.decode(dec.payload) }
                .getOrNull()
                ?.let { rd ->
                    val destinationId = dec.dest.takeIf { it != 0 } ?: this.to
                    val sourceId = dec.source.takeIf { it != 0 } ?: this.from
                    val fullRoute = listOf(destinationId) + rd.route + sourceId

                    val fullRouteBack = listOf(sourceId) + rd.route_back + destinationId
                    rd.copy(
                        route = fullRoute,
                        route_back =
                        if ((hop_start ?: 0) > 0 && rd.snr_back.isNotEmpty()) fullRouteBack else rd.route_back,
                    )
                }
        }
        return null
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

private fun RouteDiscovery.getTracerouteResponse(
    getUser: (nodeNum: Int) -> String,
    headerTowards: String = "Route traced toward destination:\n\n",
    headerBack: String = "Route traced back to us:\n\n",
): String = buildString {
    if (route.isNotEmpty()) {
        append(headerTowards)
        append(formatTraceroutePath(route.map(getUser), snr_towards))
    }
    if (route_back.isNotEmpty()) {
        append("\n\n")
        append(headerBack)
        append(formatTraceroutePath(route_back.map(getUser), snr_back))
    }
}

fun MeshPacket.getTracerouteResponse(
    getUser: (nodeNum: Int) -> String,
    headerTowards: String = "Route traced toward destination:\n\n",
    headerBack: String = "Route traced back to us:\n\n",
): String? = fullRouteDiscovery?.getTracerouteResponse(getUser, headerTowards, headerBack)

/** Returns a traceroute response string only when the result is complete (both directions). */
fun MeshPacket.getFullTracerouteResponse(
    getUser: (nodeNum: Int) -> String,
    headerTowards: String = "Route traced toward destination:\n\n",
    headerBack: String = "Route traced back to us:\n\n",
): String? = fullRouteDiscovery
    ?.takeIf { it.route.isNotEmpty() && it.route_back.isNotEmpty() }
    ?.getTracerouteResponse(getUser, headerTowards, headerBack)

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

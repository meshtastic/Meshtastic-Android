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

import co.touchlab.kermit.Logger
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.RouteDiscovery

val MeshPacket.fullRouteDiscovery: RouteDiscovery?
    get() {
        val d = decoded
        if (d != null && !d.want_response && d.portnum == PortNum.TRACEROUTE_APP) {
            val originalRd = RouteDiscovery.ADAPTER.decodeOrNull(d.payload, Logger) ?: return null

            val destinationId = if (d.dest != 0) d.dest else this.to
            val sourceId = if (d.source != 0) d.source else this.from

            // Note: Wire lists are immutable
            val fullRoute = listOf(destinationId) + originalRd.route + sourceId
            val fullRouteBack = listOf(sourceId) + originalRd.route_back + destinationId

            // hopStart was not populated prior to 2.3.0. The bitfield was added in 2.5.0 and
            // is used to detect versions where hopStart can be trusted to have been set.
            // Assuming default integer values of 0 for hop_start and snr_back_count if unset.
            val hopStartVal = hop_start
            val hasBitfield = (d.bitfield ?: 0) != 0

            return originalRd.copy(
                route = fullRoute,
                route_back =
                if ((hopStartVal > 0 || hasBitfield) && originalRd.snr_back.isNotEmpty()) {
                    fullRouteBack
                } else {
                    originalRd.route_back
                },
            )
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

package com.geeksville.mesh.model

import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshProtos.RouteDiscovery
import com.geeksville.mesh.Portnums

val MeshProtos.MeshPacket.fullRouteDiscovery: RouteDiscovery?
    get() = with(decoded) {
        if (hasDecoded() && !wantResponse && portnum == Portnums.PortNum.TRACEROUTE_APP) {
            runCatching { RouteDiscovery.parseFrom(payload).toBuilder() }.getOrNull()?.apply {
                val fullRoute = listOf(to) + routeList + from
                clearRoute()
                addAllRoute(fullRoute)

                val fullRouteBack = listOf(from) + routeBackList + to
                clearRouteBack()
                if (hopStart > 0 && snrBackCount > 0) { // otherwise back route is invalid
                    addAllRouteBack(fullRouteBack)
                }
            }?.build()
        } else {
            null
        }
    }

@Suppress("MagicNumber")
private fun formatTraceroutePath(nodesList: List<String>, snrList: List<Int>): String {
    // nodesList should include both origin and destination nodes
    // origin will not have an SNR value, but destination should
    val snrStr = if (snrList.size == nodesList.size - 1) {
        snrList
    } else {
        // use unknown SNR for entire route if snrList has invalid size
        List(nodesList.size - 1) { -128 }
    }.map { snr ->
        val str = if (snr == -128) "?" else "${snr / 4f}"
        "⇊ $str dB"
    }

    return nodesList.map { userName ->
        "■ $userName"
    }.flatMapIndexed { i, nodeStr ->
        if (i == 0) listOf(nodeStr) else listOf(snrStr[i - 1], nodeStr)
    }.joinToString("\n")
}

private fun RouteDiscovery.getTracerouteResponse(
    getUser: (nodeNum: Int) -> String,
): String = buildString {
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

fun MeshProtos.MeshPacket.getTracerouteResponse(
    getUser: (nodeNum: Int) -> String,
): String? = fullRouteDiscovery?.getTracerouteResponse(getUser)

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
package org.meshtastic.desktop.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okio.ByteString
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.service.TracerouteResponse
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NodeRequest
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import org.meshtastic.sdk.ConnectionState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.meshtastic.proto.Position as ProtoPosition

/** The requests `request` can make, each with the port its reply arrives on. */
internal enum class McpRequestKind(val wireName: String, val port: PortNum) {
    TRACEROUTE("traceroute", PortNum.TRACEROUTE_APP),
    TELEMETRY("telemetry", PortNum.TELEMETRY_APP),
    NEIGHBOR_INFO("neighbor_info", PortNum.NEIGHBORINFO_APP),
    POSITION("position", PortNum.POSITION_APP),
    NODE_INFO("node_info", PortNum.NODEINFO_APP),
    LOCAL_STATS("local_stats", PortNum.TELEMETRY_APP),
    ;

    companion object {
        fun parse(raw: String?): McpRequestKind? = entries.firstOrNull { it.wireName == raw?.trim() }
    }
}

/**
 * `request`: sends a node request through [RadioController], the calls the node detail screen's actions make, and
 * reports what the app then records: the traceroute the handler publishes, the reply row in MeshLog and the node DB, or
 * the NAK the UI would show.
 */
internal class McpRequestTool(
    private val radioController: RadioController,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val meshLogRepository: MeshLogRepository,
) : McpToolGroup {

    override fun register(server: Server) {
        server.addTool(
            name = "request",
            description =
            "Send a node request through the app's request path and wait up to wait_seconds for its outcome. kind " +
                "is traceroute, telemetry (device metrics), neighbor_info, position, node_info or local_stats; to " +
                "is a node id like !1a2b3c4d. status is answered, nak (with routing_error) or timeout; result " +
                "holds what the app recorded. Positions are reported without coordinates.",
            inputSchema =
            ToolSchema(
                properties =
                buildJsonObject {
                    putJsonObject("kind") { put("type", "string") }
                    putJsonObject("to") { put("type", "string") }
                    putJsonObject("wait_seconds") { put("type", "integer") }
                },
                required = listOf("kind", "to"),
            ),
        ) { request ->
            request(request.arguments)
        }
    }

    private suspend fun request(args: JsonObject?): CallToolResult {
        val kind = McpRequestKind.parse(args.stringArg("kind"))
        val dest = parseMcpNodeNum(args.stringArg("to"))
        val wait = (args.intArg("wait_seconds") ?: DEFAULT_WAIT_S).seconds
        return when {
            kind == null -> toolError("kind must be one of ${McpRequestKind.entries.joinToString { it.wireName }}")
            dest == null -> toolError("to must be a node id like !1a2b3c4d")
            serviceRepository.connectionState.value != ConnectionState.Connected -> toolError("not connected")
            else -> send(kind, dest, wait)
        }
    }

    private suspend fun send(kind: McpRequestKind, dest: Int, wait: Duration): CallToolResult {
        val sinceMillis = System.currentTimeMillis()
        val sent =
            try {
                Result.success(start(kind, dest))
            } catch (e: PacketQueueRejectedException) {
                Result.failure(e)
            } catch (e: LocalNodeUnavailableException) {
                Result.failure(e)
            }
        val pending = sent.getOrNull()
        return when {
            pending == null -> toolError("not sent: ${sent.exceptionOrNull()?.message}")
            pending === NodeRequest.NONE -> toolError("the app sends no ${kind.wireName} request to its own node")
            else -> toolJson(await(kind, dest, pending, wait, sinceMillis))
        }
    }

    private suspend fun await(
        kind: McpRequestKind,
        dest: Int,
        pending: NodeRequest,
        wait: Duration,
        sinceMillis: Long,
    ): JsonObject {
        val started = TimeSource.Monotonic.markNow()
        val outcome =
            withTimeoutOrNull(wait) {
                merge(nakOf(pending), replyOf(kind, dest, pending.requestId, sinceMillis)).first()
            }
        return buildJsonObject {
            put("kind", kind.wireName)
            put("to", NodeAddress.numToDefaultId(dest))
            put("request_id", pending.requestId?.toUInt()?.toLong())
            put("status", outcome?.status ?: "timeout")
            put("routing_error", outcome?.routingError)
            put("elapsed_ms", started.elapsedNow().inWholeMilliseconds)
            outcome?.result?.let { put("result", it) }
        }
    }

    private suspend fun start(kind: McpRequestKind, dest: Int): NodeRequest = when (kind) {
        McpRequestKind.TRACEROUTE -> radioController.requestTraceroute(dest)
        McpRequestKind.TELEMETRY -> radioController.requestTelemetry(dest, TelemetryType.DEVICE)
        McpRequestKind.NEIGHBOR_INFO -> radioController.requestNeighborInfo(dest)
        McpRequestKind.POSITION -> radioController.requestPosition(dest, Position(0.0, 0.0, 0))
        McpRequestKind.NODE_INFO -> radioController.requestUserInfo(dest)
        McpRequestKind.LOCAL_STATS -> radioController.requestTelemetry(dest, TelemetryType.LOCAL_STATS)
    }

    private fun nakOf(pending: NodeRequest): Flow<Outcome> = flow {
        pending.awaitRoutingError()?.let { code ->
            emit(Outcome("nak", Routing.Error.fromValue(code)?.name ?: code.toString(), null))
        }
    }

    private fun replyOf(kind: McpRequestKind, dest: Int, requestId: Int?, sinceMillis: Long): Flow<Outcome> {
        val myNum = nodeRepository.myNodeInfo.value?.myNodeNum
        val replies =
            if (kind == McpRequestKind.TRACEROUTE) {
                serviceRepository.tracerouteResponse
                    .filterNotNull()
                    .filter { it.requestId == requestId }
                    .map(::traceroute)
            } else {
                val from = if (dest == myNum) MeshLog.NODE_NUM_LOCAL else dest
                meshLogRepository
                    .getLogsFrom(from, kind.port.value)
                    .mapNotNull { logs -> logs.firstOrNull { isMcpReply(it, kind, requestId, sinceMillis, myNum) } }
                    .map { replyJson(kind, dest, it) }
            }
        return replies.map { Outcome("answered", null, it) }
    }

    private fun traceroute(response: TracerouteResponse) = buildJsonObject {
        putJsonArray("route") { response.forwardRoute.forEach { add(NodeAddress.numToDefaultId(it)) } }
        putJsonArray("route_back") { response.returnRoute.forEach { add(NodeAddress.numToDefaultId(it)) } }
        put("message", response.message)
    }

    private fun replyJson(kind: McpRequestKind, dest: Int, log: MeshLog): JsonObject {
        val payload = log.fromRadio.packet?.decoded?.payload ?: ByteString.EMPTY
        val node = nodeRepository.nodeDBbyNum.value[dest]
        return buildJsonObject {
            put("log_uuid", log.uuid)
            when (kind) {
                McpRequestKind.TELEMETRY -> {
                    put("reply", mcpJson(Telemetry.ADAPTER.decode(payload).device_metrics))
                    put("node_db", mcpJson(node?.deviceMetrics))
                }

                McpRequestKind.LOCAL_STATS -> put("reply", mcpJson(Telemetry.ADAPTER.decode(payload).local_stats))

                McpRequestKind.NEIGHBOR_INFO -> {
                    val info = NeighborInfo.ADAPTER.decode(payload)
                    put("node_broadcast_interval_secs", info.node_broadcast_interval_secs)
                    put("neighbors", neighborsJson(info))
                }

                McpRequestKind.POSITION -> {
                    val reply = ProtoPosition.ADAPTER.decode(payload)
                    put("reply_time", reply.time)
                    put("reply_precision_bits", reply.precision_bits)
                    put("node_db_time", node?.position?.time)
                    put("node_db_has_position", node?.validPosition != null)
                }

                McpRequestKind.NODE_INFO -> {
                    put("reply", mcpJson(User.ADAPTER.decode(payload)))
                    put("node_db", mcpJson(node?.user))
                }

                McpRequestKind.TRACEROUTE -> Unit
            }
        }
    }

    private class Outcome(val status: String, val routingError: String?, val result: JsonObject?)

    private companion object {
        const val DEFAULT_WAIT_S = 60
    }
}

private fun neighborsJson(info: NeighborInfo) = JsonArray(
    info.neighbors.map { n ->
        buildJsonObject {
            put("id", NodeAddress.numToDefaultId(n.node_id))
            put("snr", n.snr)
        }
    },
)

/**
 * Whether [log] is the reply to a [kind] request. A request with a wire id is matched on the reply's `request_id`;
 * telemetry, whose id the SDK does not expose, takes the first reply addressed to us since [sinceMillis] that carries
 * the variant asked for. Our own request rows never match.
 */
internal fun isMcpReply(
    log: MeshLog,
    kind: McpRequestKind,
    requestId: Int?,
    sinceMillis: Long,
    myNodeNum: Int?,
): Boolean {
    val packet = log.fromRadio.packet
    val data = packet?.decoded
    return when {
        data == null || data.portnum != kind.port || data.want_response -> false
        requestId != null && requestId != 0 -> data.request_id == requestId
        else -> log.received_date >= sinceMillis && packet.to == myNodeNum && carriesVariant(kind, data.payload)
    }
}

private fun carriesVariant(kind: McpRequestKind, payload: ByteString): Boolean {
    val telemetry = runCatching { Telemetry.ADAPTER.decode(payload) }.getOrNull()
    return when (kind) {
        McpRequestKind.TELEMETRY -> telemetry?.device_metrics != null
        McpRequestKind.LOCAL_STATS -> telemetry?.local_stats != null
        else -> false
    }
}

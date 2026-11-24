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
package org.meshtastic.core.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRequest
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.QueryController
import org.meshtastic.core.repository.REMOTE_READ_TIMEOUT
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.repository.routingErrorOrNull
import org.meshtastic.core.repository.toNodeRequest
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.MessageHandle
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RoutingApi
import org.meshtastic.sdk.TelemetryApi
import org.meshtastic.sdk.requestPaxcount
import org.meshtastic.proto.Position as ProtoPosition

/**
 * [QueryController] implementation over the active session's SDK client: `TelemetryApi`, `RoutingApi`, the request
 * helpers and the admin metadata read. Traceroute, neighbor-info, position and user-info requests go out under an ID
 * the client draws with `nextPacketId()`, and settle on the reply or routing error. The session's outbound packet log
 * records every request as written.
 */
@Suppress("LongParameterList")
internal class QueryControllerImpl(
    private val radio: RadioInterfaceService,
    private val connectionStateProvider: ConnectionStateProvider,
    private val tracerouteHandler: TracerouteHandler,
    private val neighborInfoHandler: NeighborInfoHandler,
    private val nodeManager: NodeManager,
    private val uiPrefs: UiPrefs,
    private val scope: CoroutineScope,
) : QueryController {

    private fun isLocal(destNum: Int): Boolean = destNum == nodeManager.myNodeNum.value

    /** The session's client once the app has installed its snapshot, so a request can read the local node. */
    private fun requireClient(operation: String): RadioClient =
        radio.activeSession.value
            ?.takeIf { connectionStateProvider.connectionState.value == ConnectionState.Connected }
            ?.let(radio::clientForSession) ?: throw PacketQueueRejectedException(operation)

    /** Sends through the client's request helper; an SDK refusal reads as the session refusing admission. */
    private suspend fun send(operation: String, request: suspend RadioClient.() -> MessageHandle): NodeRequest {
        val client = requireClient(operation)
        val handle =
            try {
                client.request()
            } catch (e: MeshtasticException) {
                throw PacketQueueRejectedException(operation).apply { addSuppressed(e) }
            }
        return handle.toNodeRequest()
    }

    /** Starts [rpc] on [client] and returns once the SDK has queued it; the reply settles the [NodeRequest]. */
    private fun rpc(operation: String, rpc: suspend RadioClient.() -> AdminResult<*>): NodeRequest =
        rpc(requireClient(operation), requestId = null, rpc)

    private fun rpc(client: RadioClient, requestId: Int?, rpc: suspend RadioClient.() -> AdminResult<*>): NodeRequest {
        val outcome =
            scope.async(start = CoroutineStart.UNDISPATCHED) {
                try {
                    client.rpc().routingErrorOrNull()
                } catch (_: MeshtasticException) {
                    null
                }
            }
        return NodeRequest(requestId) { outcome.await() }
    }

    override suspend fun refreshMetadata(destNum: Int): NodeRequest {
        val local = isLocal(destNum)
        return rpc("Metadata request") {
            val target = if (local) admin else admin.forNode(NodeId(destNum)).withRpcTimeout(REMOTE_READ_TIMEOUT)
            target.getDeviceMetadata()
        }
    }

    override suspend fun requestPosition(destNum: Int, currentPosition: Position): NodeRequest {
        if (isLocal(destNum)) return NodeRequest.NONE
        val myNodeNum = nodeManager.myNodeNum.value ?: 0
        val ours =
            if (uiPrefs.shouldProvideNodeLocation(myNodeNum).value) {
                currentPosition.takeIf { it.isValid() }
                    ?: nodeManager.nodeDBbyNodeNum[myNodeNum]?.position?.let { Position(it) }?.takeIf { it.isValid() }
            } else {
                null
            }
        // Firmware coarsens a phone-originated position to the channel precision, so an explicit 0,0 goes out as a
        // real-looking coordinate. Without a valid fix only the time is shared.
        val shared =
            ProtoPosition.Builder()
                .also { wb ->
                    if (ours != null) {
                        wb.latitude_i = Position.degI(ours.latitude)
                        wb.longitude_i = Position.degI(ours.longitude)
                        wb.altitude = ours.altitude
                    }
                    wb.time = (nowMillis / TimeConstants.MS_PER_SEC).toInt()
                }
                .build()
        val client = requireClient("Position request")
        val node = NodeId(destNum)
        client.requireRemote(node, "Position request")
        val requestId = client.nextPacketId().raw
        return rpc(client, requestId) { routingFor(local = false).requestPosition(node, null, shared, requestId) }
    }

    override suspend fun requestUserInfo(destNum: Int): NodeRequest {
        val myNum = nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("User-info request")
        if (destNum == myNum) return NodeRequest.NONE
        val client = requireClient("User-info request")
        // The SDK sends its own record of our user, so that is the one checked.
        if (client.ownNode.value?.user == null) throw LocalNodeUnavailableException("User-info request")
        val node = NodeId(destNum)
        client.requireRemote(node, "User-info request")
        val requestId = client.nextPacketId().raw
        return rpc(client, requestId) { routingFor(local = false).requestNodeInfo(node, null, requestId) }
    }

    override suspend fun requestTraceroute(destNum: Int): NodeRequest {
        val client = requireClient("Traceroute request")
        val requestId = client.nextPacketId().raw
        tracerouteHandler.recordStartTime(requestId, destNum)
        return rpc(client, requestId) {
            routingFor(isLocal(destNum)).traceRoute(NodeId(destNum), requestId = requestId)
        }
    }

    override suspend fun requestTelemetry(destNum: Int, type: TelemetryType): NodeRequest {
        val node = NodeId(destNum)
        if (type == TelemetryType.PAX) {
            return send("Paxcount request") { requestPaxcount(node) }
        }
        val local = isLocal(destNum)
        return rpc("Telemetry request") {
            val telemetry = telemetryFor(local)
            when (type) {
                TelemetryType.LOCAL_STATS -> telemetry.requestLocalStats(node)
                TelemetryType.DEVICE -> telemetry.requestDevice(node)
                TelemetryType.ENVIRONMENT -> telemetry.requestEnvironment(node)
                TelemetryType.AIR_QUALITY -> telemetry.requestAirQuality(node)
                TelemetryType.POWER -> telemetry.requestPower(node)
                TelemetryType.HOST -> telemetry.requestHost(node)
                TelemetryType.PAX -> error("Paxcount is not a telemetry variant")
            }
        }
    }

    override suspend fun requestNeighborInfo(destNum: Int): NodeRequest {
        val client = requireClient("Neighbor-info request")
        val requestId = client.nextPacketId().raw
        neighborInfoHandler.recordStartTime(requestId)
        return rpc(client, requestId) {
            routingFor(isLocal(destNum)).requestNeighborInfo(NodeId(destNum), requestId = requestId)
        }
    }
}

/** The SDK throws for a request to the node it knows as ours, after the call returns; refuse it before it starts. */
private fun RadioClient.requireRemote(node: NodeId, operation: String) {
    if (ownNode.value?.num == node.raw) throw PacketQueueRejectedException(operation)
}

/** A request to another node waits [REMOTE_READ_TIMEOUT], so a NAK after a multi-hop retransmit still settles it. */
private fun RadioClient.routingFor(local: Boolean): RoutingApi =
    if (local) routing else routing.withRpcTimeout(REMOTE_READ_TIMEOUT)

private fun RadioClient.telemetryFor(local: Boolean): TelemetryApi =
    if (local) telemetry else telemetry.withRpcTimeout(REMOTE_READ_TIMEOUT)

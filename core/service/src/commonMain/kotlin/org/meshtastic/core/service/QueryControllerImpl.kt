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
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRequest
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.QueryController
import org.meshtastic.core.repository.REMOTE_READ_TIMEOUT
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.repository.routingErrorOrNull
import org.meshtastic.core.repository.toNodeRequest
import org.meshtastic.proto.PortNum
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.MessageHandle
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.requestNodeInfoOnHeardChannel
import org.meshtastic.sdk.requestPaxcount
import org.meshtastic.sdk.requestPositionOnHeardChannel
import org.meshtastic.proto.Position as ProtoPosition

/**
 * [QueryController] implementation over the active session's SDK client: `TelemetryApi`, `RoutingApi`, the request
 * helpers and the admin metadata read. Traceroute and neighbor-info requests go out under an ID from [packetHandler],
 * which logs them where the request logs read them and times the reply.
 */
@Suppress("LongParameterList")
internal class QueryControllerImpl(
    private val radio: RadioInterfaceService,
    private val packetHandler: PacketHandler,
    private val tracerouteHandler: TracerouteHandler,
    private val neighborInfoHandler: NeighborInfoHandler,
    private val nodeManager: NodeManager,
    private val uiPrefs: UiPrefs,
    private val scope: CoroutineScope,
) : QueryController {

    private fun isLocal(destNum: Int): Boolean = destNum == nodeManager.myNodeNum.value

    private fun requireClient(operation: String): RadioClient =
        radio.activeSession.value?.let(radio::clientForSession) ?: throw PacketQueueRejectedException(operation)

    /** Sends through [client]'s request helper; an SDK refusal reads as the session refusing admission. */
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
        // real-looking coordinate. Without a valid fix only the time is shared, which the SDK stamps.
        val shared =
            ProtoPosition.Builder()
                .also { wb ->
                    if (ours != null) {
                        wb.latitude_i = Position.degI(ours.latitude)
                        wb.longitude_i = Position.degI(ours.longitude)
                        wb.altitude = ours.altitude
                    }
                }
                .build()
        return send("Position request") { requestPositionOnHeardChannel(NodeId(destNum), shared) }
    }

    override suspend fun requestUserInfo(destNum: Int): NodeRequest {
        if (isLocal(destNum)) return NodeRequest.NONE
        return send("User-info request") { requestNodeInfoOnHeardChannel(NodeId(destNum)) }
    }

    override suspend fun requestTraceroute(destNum: Int): NodeRequest {
        val client = requireClient("Traceroute request")
        val requestId = packetHandler.generatePacketId()
        tracerouteHandler.recordStartTime(requestId, destNum)
        val request = rpc(client, requestId) { routing.traceRoute(NodeId(destNum), requestId = requestId) }
        packetHandler.logRequest(requestId, destNum, PortNum.TRACEROUTE_APP)
        return request
    }

    override suspend fun requestTelemetry(destNum: Int, type: TelemetryType): NodeRequest {
        val node = NodeId(destNum)
        return when (type) {
            TelemetryType.PAX -> send("Paxcount request") { requestPaxcount(node) }
            TelemetryType.LOCAL_STATS -> rpc("Telemetry request") { telemetry.requestLocalStats(node) }
            TelemetryType.DEVICE -> rpc("Telemetry request") { telemetry.requestDevice(node) }
            TelemetryType.ENVIRONMENT -> rpc("Telemetry request") { telemetry.requestEnvironment(node) }
            TelemetryType.AIR_QUALITY -> rpc("Telemetry request") { telemetry.requestAirQuality(node) }
            TelemetryType.POWER -> rpc("Telemetry request") { telemetry.requestPower(node) }
            TelemetryType.HOST -> rpc("Telemetry request") { telemetry.requestHost(node) }
        }
    }

    override suspend fun requestNeighborInfo(destNum: Int): NodeRequest {
        val client = requireClient("Neighbor-info request")
        val requestId = packetHandler.generatePacketId()
        neighborInfoHandler.recordStartTime(requestId)
        val request = rpc(client, requestId) { routing.requestNeighborInfo(NodeId(destNum), requestId = requestId) }
        packetHandler.logRequest(requestId, destNum, PortNum.NEIGHBORINFO_APP)
        return request
    }
}

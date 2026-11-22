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
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.repository.LocalNodeUnavailableException
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
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HostMetrics
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket.Priority.BACKGROUND
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.PowerMetrics
import org.meshtastic.proto.Telemetry
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.MessageHandle
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RoutingApi
import org.meshtastic.sdk.TelemetryApi
import org.meshtastic.sdk.channelFor
import org.meshtastic.sdk.requestPaxcount
import org.meshtastic.sdk.requestPositionOnHeardChannel
import org.meshtastic.proto.Position as ProtoPosition
import org.meshtastic.sdk.ConnectionState as SdkConnectionState

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

    /**
     * The session's client once its handshake is Ready; the SDK refuses sends and answers RPCs as unreachable before.
     */
    private fun requireClient(operation: String): RadioClient =
        radio.activeSession.value?.let(radio::clientForSession)?.takeIf {
            it.connection.value == SdkConnectionState.Connected
        } ?: throw PacketQueueRejectedException(operation)

    /**
     * Sends through [client]'s request helper and logs what went out; an SDK refusal reads as the session refusing
     * admission.
     */
    private suspend fun send(
        operation: String,
        log: (id: Int) -> Unit,
        request: suspend RadioClient.() -> MessageHandle,
    ): NodeRequest {
        val client = requireClient(operation)
        val handle =
            try {
                client.request()
            } catch (e: MeshtasticException) {
                throw PacketQueueRejectedException(operation).apply { addSuppressed(e) }
            } catch (e: IllegalArgumentException) {
                // The SDK refuses a request addressed to the node it knows as ours.
                throw PacketQueueRejectedException(operation).apply { addSuppressed(e) }
            }
        log(handle.id.raw)
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
                    wb.time = (nowMillis / TimeConstants.MS_PER_SEC).toInt()
                }
                .build()
        return send(
            "Position request",
            log = { id ->
                val payload = shared.encode().toByteString()
                packetHandler.logSent(id, destNum, PortNum.POSITION_APP, payload, true, priority = BACKGROUND)
            },
        ) {
            requestPositionOnHeardChannel(NodeId(destNum), shared)
        }
    }

    override suspend fun requestUserInfo(destNum: Int): NodeRequest {
        val myNum = nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("User-info request")
        if (destNum == myNum) return NodeRequest.NONE
        val user = nodeManager.nodeDBbyNodeNum[myNum]?.user ?: throw LocalNodeUnavailableException("User-info request")
        val node = NodeId(destNum)
        return send(
            "User-info request",
            log = { id ->
                packetHandler.logSent(id, destNum, PortNum.NODEINFO_APP, user.encode().toByteString(), true)
            },
        ) {
            requestNodeInfo(node, channelFor(node), user)
        }
    }

    override suspend fun requestTraceroute(destNum: Int): NodeRequest {
        val client = requireClient("Traceroute request")
        val requestId = packetHandler.generatePacketId()
        tracerouteHandler.recordStartTime(requestId, destNum)
        val request =
            rpc(client, requestId) {
                routingFor(isLocal(destNum)).traceRoute(NodeId(destNum), requestId = requestId)
            }
        packetHandler.logSent(requestId, destNum, PortNum.TRACEROUTE_APP, wantResponse = true, dest = destNum)
        return request
    }

    override suspend fun requestTelemetry(destNum: Int, type: TelemetryType): NodeRequest {
        val node = NodeId(destNum)
        if (type == TelemetryType.PAX) {
            val paxcount = Paxcount.Builder().build().encode().toByteString()
            return send(
                "Paxcount request",
                log = { id -> packetHandler.logSent(id, destNum, PortNum.PAXCOUNTER_APP, paxcount, true, destNum) },
            ) {
                requestPaxcount(node)
            }
        }
        val local = isLocal(destNum)
        val request =
            rpc("Telemetry request") {
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
        // TelemetryApi does not expose the wire ID it allocated.
        packetHandler.logSent(0, destNum, PortNum.TELEMETRY_APP, telemetryRequest(type), true, destNum)
        return request
    }

    override suspend fun requestNeighborInfo(destNum: Int): NodeRequest {
        val client = requireClient("Neighbor-info request")
        val requestId = packetHandler.generatePacketId()
        neighborInfoHandler.recordStartTime(requestId)
        val request =
            rpc(client, requestId) {
                routingFor(isLocal(destNum)).requestNeighborInfo(NodeId(destNum), requestId = requestId)
            }
        packetHandler.logSent(requestId, destNum, PortNum.NEIGHBORINFO_APP, wantResponse = true, dest = destNum)
        return request
    }
}

/** A request to another node waits [REMOTE_READ_TIMEOUT], so a NAK after a multi-hop retransmit still settles it. */
private fun RadioClient.routingFor(local: Boolean): RoutingApi =
    if (local) routing else routing.withRpcTimeout(REMOTE_READ_TIMEOUT)

private fun RadioClient.telemetryFor(local: Boolean): TelemetryApi =
    if (local) telemetry else telemetry.withRpcTimeout(REMOTE_READ_TIMEOUT)

/** The empty variant [type] names, as TelemetryApi sends it. */
private fun telemetryRequest(type: TelemetryType): ByteString = Telemetry.Builder()
    .also { wb ->
        when (type) {
            TelemetryType.DEVICE -> wb.device_metrics = DeviceMetrics.Builder().build()
            TelemetryType.ENVIRONMENT -> wb.environment_metrics = EnvironmentMetrics.Builder().build()
            TelemetryType.AIR_QUALITY -> wb.air_quality_metrics = AirQualityMetrics.Builder().build()
            TelemetryType.POWER -> wb.power_metrics = PowerMetrics.Builder().build()
            TelemetryType.LOCAL_STATS -> wb.local_stats = LocalStats.Builder().build()
            TelemetryType.HOST -> wb.host_metrics = HostMetrics.Builder().build()
            TelemetryType.PAX -> Unit
        }
    }
    .build()
    .encode()
    .toByteString()

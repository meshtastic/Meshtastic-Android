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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRequest
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.repository.toNodeRequest
import org.meshtastic.proto.Data
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Neighbor
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry

@Single
class CommandSenderImpl(
    private val packetHandler: PacketHandler,
    private val nodeManager: NodeManager,
    private val tracerouteHandler: TracerouteHandler,
    private val neighborInfoHandler: NeighborInfoHandler,
    radioConfigRepository: RadioConfigRepository,
    scope: ServiceScope,
) : CommandSender {
    private val localConfig = MutableStateFlow(LocalConfig.Builder().build())

    init {
        radioConfigRepository.localConfigFlow.onEach { localConfig.value = it }.launchIn(scope)
    }

    private fun hopLimit(): Int = (localConfig.value.lora?.hop_limit ?: 0).takeIf { it > 0 } ?: DEFAULT_HOP_LIMIT

    /** Protocol requests stay off PKI: relays must read the payload, and a traceroute appends each hop to it. */
    private fun heardChannel(nodeNum: Int): Int = nodeManager.nodeDBbyNodeNum[nodeNum]?.channel ?: 0

    override suspend fun requestTraceroute(destNum: Int): NodeRequest {
        val request =
            send(
                "Traceroute request",
                packet(destNum, wantAck = true) { wb ->
                    wb.portnum = PortNum.TRACEROUTE_APP
                    wb.want_response = true
                    wb.dest = destNum
                },
            )
        request.requestId?.let { tracerouteHandler.recordStartTime(it, destNum) }
        return request
    }

    override suspend fun requestNeighborInfo(destNum: Int): NodeRequest {
        val myNum = nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("Neighbor-info request")
        val request =
            send(
                "Neighbor-info request",
                packet(destNum, wantAck = true) { wb ->
                    wb.portnum = PortNum.NEIGHBORINFO_APP
                    wb.want_response = true
                    if (destNum == myNum) {
                        // Firmware merges a received table into its own unless it is this one-dummy-neighbor shape.
                        wb.payload =
                            (neighborInfoHandler.lastNeighborInfo ?: dummyNeighborInfo(myNum)).encode().toByteString()
                    } else {
                        wb.dest = destNum
                    }
                },
            )
        request.requestId?.let(neighborInfoHandler::recordStartTime)
        return request
    }

    override suspend fun requestLocalStats(destNum: Int): NodeRequest = send(
        "Telemetry request",
        packet(destNum, wantAck = false) { wb ->
            wb.portnum = PortNum.TELEMETRY_APP
            wb.payload =
                Telemetry.Builder()
                    .also { it.local_stats = LocalStats.Builder().build() }
                    .build()
                    .encode()
                    .toByteString()
            wb.want_response = true
            wb.dest = destNum
        },
    )

    private fun dummyNeighborInfo(myNum: Int): NeighborInfo {
        val oneHour = TimeConstants.SECONDS_PER_HOUR
        Logger.d { "No stored neighbor info from connected radio, sending dummy data" }
        return NeighborInfo.Builder()
            .also { wb ->
                wb.node_id = myNum
                wb.last_sent_by_id = myNum
                wb.node_broadcast_interval_secs = oneHour
                wb.neighbors =
                    listOf(
                        Neighbor.Builder()
                            .also { nb ->
                                nb.node_id = 0
                                nb.snr = 0f
                                nb.last_rx_time = (nowMillis / TimeConstants.MS_PER_SEC).toInt()
                                nb.node_broadcast_interval_secs = oneHour
                            }
                            .build(),
                    )
            }
            .build()
    }

    private fun packet(to: Int, wantAck: Boolean, decoded: (Data.Builder) -> Unit): MeshPacket {
        val hops = hopLimit()
        return MeshPacket.Builder()
            .also { wb ->
                wb.from = nodeManager.myNodeNum.value ?: 0
                wb.to = to
                wb.id = packetHandler.generatePacketId()
                wb.want_ack = wantAck
                wb.hop_limit = hops
                wb.hop_start = hops
                wb.channel = heardChannel(to)
                wb.decoded = Data.Builder().also { it.payload = ByteString.EMPTY }.also(decoded).build()
            }
            .build()
    }

    private suspend fun send(operation: String, packet: MeshPacket): NodeRequest =
        packetHandler.sendTracked(packet)?.toNodeRequest() ?: throw PacketQueueRejectedException(operation)

    private companion object {
        const val DEFAULT_HOP_LIMIT = 3
    }
}

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
package org.meshtastic.feature.node.metrics

import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.MeshLog
import org.meshtastic.feature.node.model.NeighborInfoEntry
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Neighbor
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals

class NeighborInfoChartTest {

    @Test
    fun eachReportedNeighborListBecomesOnePointOldestFirst() {
        val later = NeighborInfoEntry.Received(report(uuid = "b", receivedMs = 20_000, snrs = listOf(4f, 8f)))
        val earlier = NeighborInfoEntry.Received(report(uuid = "a", receivedMs = 10_000, snrs = emptyList()))

        assertEquals(
            listOf(
                NeighborInfoChartPoint(timeSeconds = 10.0, neighborCount = 0, meanSnr = null),
                NeighborInfoChartPoint(timeSeconds = 20.0, neighborCount = 2, meanSnr = 6.0),
            ),
            buildNeighborInfoChartPoints(listOf(later, earlier)),
        )
    }

    @Test
    fun anUnansweredRequestPlotsNothing() {
        val request = NeighborInfoEntry.Requested(report(uuid = "r", receivedMs = 1_000, snrs = null), reply = null)

        assertEquals(emptyList(), buildNeighborInfoChartPoints(listOf(request)))
    }

    private fun report(uuid: String, receivedMs: Long, snrs: List<Float>?) = MeshLog(
        uuid = uuid,
        message_type = "Packet",
        received_date = receivedMs,
        raw_message = "",
        fromNum = 2,
        portNum = PortNum.NEIGHBORINFO_APP.value,
        fromRadio =
        FromRadio.Builder()
            .also { radio ->
                radio.packet =
                    MeshPacket.Builder()
                        .also { packet ->
                            packet.decoded =
                                Data.Builder()
                                    .also { data ->
                                        data.portnum = PortNum.NEIGHBORINFO_APP
                                        snrs?.let { values ->
                                            data.payload =
                                                NeighborInfo.Builder()
                                                    .also { info ->
                                                        info.neighbors = values.mapIndexed { i, snr ->
                                                            Neighbor.Builder()
                                                                .also {
                                                                    it.node_id = i + 1
                                                                    it.snr = snr
                                                                }
                                                                .build()
                                                        }
                                                    }
                                                    .build()
                                                    .encode()
                                                    .toByteString()
                                        }
                                    }
                                    .build()
                        }
                        .build()
            }
            .build(),
    )
}

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
package org.meshtastic.feature.node.model

import org.meshtastic.core.model.MeshLog
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsStateTest {

    @Test
    fun hasLocalStatsReturnsTrueWhenLocalStatsTelemetryExists() {
        val state = MetricsState(localStats = listOf(localStatsTelemetry(time = 123)))

        assertTrue(state.hasLocalStats())
    }

    @Test
    fun oldestTimestampIncludesLocalStatsTelemetry() {
        val state =
            MetricsState(
                deviceMetrics = listOf(Telemetry.Builder().also { wb -> wb.time = 200 }.build()),
                localStats = listOf(localStatsTelemetry(time = 100)),
            )

        assertEquals(100L, state.oldestTimestampSeconds())
    }

    @Test
    fun neighborInfoEntriesPairRequestsWithRepliesAndListUnaskedNeighborInfo() {
        val request = neighborLog(uuid = "request", packetId = 10, receivedDate = 1_000)
        val reply = neighborLog(uuid = "reply", packetId = 11, requestId = 10, receivedDate = 2_000)
        val broadcast = neighborLog(uuid = "broadcast", packetId = 12, receivedDate = 3_000)
        val state = MetricsState(neighborInfoRequests = listOf(request), neighborInfoResults = listOf(reply, broadcast))

        assertEquals(
            listOf(NeighborInfoEntry.Received(broadcast), NeighborInfoEntry.Requested(request, reply)),
            state.neighborInfoEntries(),
        )
    }

    @Test
    fun aSecondReportAnsweringTheSameRequestIsStillListed() {
        val request = neighborLog(uuid = "request", packetId = 10, receivedDate = 1_000)
        val first = neighborLog(uuid = "first", packetId = 11, requestId = 10, receivedDate = 2_000)
        val second = neighborLog(uuid = "second", packetId = 12, requestId = 10, receivedDate = 3_000)
        val state = MetricsState(neighborInfoRequests = listOf(request), neighborInfoResults = listOf(first, second))

        assertEquals(
            listOf(NeighborInfoEntry.Received(second), NeighborInfoEntry.Requested(request, first)),
            state.neighborInfoEntries(),
        )
    }

    @Test
    fun neighborInfoLogsExistWithOnlyUnaskedNeighborInfo() {
        val state = MetricsState(neighborInfoResults = listOf(neighborLog(uuid = "b", packetId = 1, receivedDate = 1)))

        assertTrue(state.hasNeighborInfoLogs())
    }

    private fun neighborLog(uuid: String, packetId: Int, receivedDate: Long, requestId: Int = 0) = MeshLog(
        uuid = uuid,
        message_type = "Packet",
        received_date = receivedDate,
        raw_message = "",
        fromNum = 2,
        portNum = PortNum.NEIGHBORINFO_APP.value,
        fromRadio =
        FromRadio.Builder()
            .also { radio ->
                radio.packet =
                    MeshPacket.Builder()
                        .also { packet ->
                            packet.id = packetId
                            packet.decoded =
                                Data.Builder()
                                    .also { data ->
                                        data.portnum = PortNum.NEIGHBORINFO_APP
                                        data.request_id = requestId
                                    }
                                    .build()
                        }
                        .build()
            }
            .build(),
    )

    private fun localStatsTelemetry(time: Int) = Telemetry.Builder()
        .also { wb ->
            wb.time = time
            wb.local_stats = LocalStats.Builder().also { wb -> wb.noise_floor = -101 }.build()
        }
        .build()
}

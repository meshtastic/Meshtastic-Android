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

import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Duplicate LazyColumn keys are a fatal `IllegalArgumentException`, and `MeshPacket.id` is unique only per originating
 * node, with retransmissions stored per reception.
 */
class SignalLogKeyTest {

    @Test
    fun `repeated packet ids still produce distinct keys`() {
        val packets = listOf(packet(id = 163275746), packet(id = 163275746), packet(id = 163275746))

        val keys = buildSignalLog(signalData = packets, localStatsData = emptyList()).map { it.key }

        assertEquals(3, keys.toSet().size, "duplicate LazyColumn keys crash the screen: $keys")
    }

    @Test
    fun `a negative packet id is not conflated with its positive twin`() {
        // Packet ids are signed in the proto, so the sign has to survive into the key. Both packets sit at index 0 so
        // the index cannot be what separates them.
        val negative = buildSignalLog(signalData = listOf(packet(id = -1344023121)), localStatsData = emptyList())
        val positive = buildSignalLog(signalData = listOf(packet(id = 1344023121)), localStatsData = emptyList())

        assertEquals("packet_-1344023121_0", negative.single().key)
        assertEquals("packet_1344023121_0", positive.single().key)
    }

    @Test
    fun `local stats and packets sharing a timestamp do not collide`() {
        val keys =
            buildSignalLog(
                signalData = listOf(packet(id = 7, rxTime = 100), packet(id = 7, rxTime = 100)),
                localStatsData = listOf(stats(time = 100), stats(time = 100)),
            )
                .map { it.key }

        assertEquals(4, keys.toSet().size, "same-second entries across both types must stay distinct: $keys")
    }

    @Test
    fun `entries are ordered newest first across both types`() {
        val log =
            buildSignalLog(
                signalData = listOf(packet(id = 1, rxTime = 10), packet(id = 2, rxTime = 30)),
                localStatsData = listOf(stats(time = 20)),
            )

        assertEquals(listOf(30, 20, 10), log.map { it.timeSeconds })
    }

    private fun packet(id: Int, rxTime: Int = 0) = MeshPacket(id = id, rx_time = rxTime)

    private fun stats(time: Int) = Telemetry(time = time)
}

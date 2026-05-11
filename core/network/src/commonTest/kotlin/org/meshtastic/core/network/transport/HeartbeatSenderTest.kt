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
package org.meshtastic.core.network.transport

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.ToRadio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class HeartbeatSenderTest {

    @Test
    fun `sendHeartbeat encodes a heartbeat and runs afterHeartbeat after sending`() = runTest {
        val sentPackets = mutableListOf<ByteArray>()
        var afterHeartbeatCalls = 0
        val sender =
            HeartbeatSender(
                sendToRadio = { sentPackets.add(it) },
                afterHeartbeat = {
                    afterHeartbeatCalls++
                    assertEquals(1, sentPackets.size)
                },
            )

        sender.sendHeartbeat()

        assertEquals(1, sentPackets.size)
        assertEquals(1, afterHeartbeatCalls)

        val message = ToRadio.ADAPTER.decode(sentPackets.single())
        val heartbeat = assertNotNull(message.heartbeat)
        assertEquals(0, heartbeat.nonce)
        assertNull(message.packet)
    }

    @Test
    fun `heartbeat loop emits at the configured interval`() = runTest {
        val sentPackets = mutableListOf<ByteArray>()
        val sender = HeartbeatSender(sendToRadio = { sentPackets.add(it) })
        val interval = 5.seconds
        val job = launchFiniteHeartbeatLoop(sender = sender, interval = interval, repeatCount = 3)

        runCurrent()
        assertHeartbeats(sentPackets, 0)

        advanceTimeBy(interval.inWholeMilliseconds)
        runCurrent()
        assertHeartbeats(sentPackets, 0, 1)

        advanceTimeBy(interval.inWholeMilliseconds)
        runCurrent()
        assertHeartbeats(sentPackets, 0, 1, 2)

        job.cancel()
    }

    @Test
    fun `cancelling a heartbeat loop stops additional heartbeats`() = runTest {
        val sentPackets = mutableListOf<ByteArray>()
        val sender = HeartbeatSender(sendToRadio = { sentPackets.add(it) })
        val interval = 5.seconds
        val job = launchRepeatingHeartbeatLoop(sender = sender, interval = interval)

        runCurrent()
        advanceTimeBy(interval.inWholeMilliseconds)
        runCurrent()
        assertHeartbeats(sentPackets, 0, 1)

        job.cancel()
        advanceTimeBy(interval.inWholeMilliseconds * 5)
        runCurrent()

        assertHeartbeats(sentPackets, 0, 1)
    }

    @Test
    fun `zero interval sends all scheduled heartbeats without advancing time`() = runTest {
        val sentPackets = mutableListOf<ByteArray>()
        val sender = HeartbeatSender(sendToRadio = { sentPackets.add(it) })

        backgroundScope.launch { repeat(3) { sender.sendHeartbeat() } }
        runCurrent()

        assertEquals(0L, testScheduler.currentTime)
        assertHeartbeats(sentPackets, 0, 1, 2)
    }

    @Test
    fun `restarting a heartbeat loop resumes with the next nonce`() = runTest {
        val sentPackets = mutableListOf<ByteArray>()
        val sender = HeartbeatSender(sendToRadio = { sentPackets.add(it) })
        val interval = 5.seconds

        val firstJob = launchRepeatingHeartbeatLoop(sender = sender, interval = interval)
        runCurrent()
        advanceTimeBy(interval.inWholeMilliseconds)
        runCurrent()
        firstJob.cancel()

        val secondJob = launchFiniteHeartbeatLoop(sender = sender, interval = interval, repeatCount = 2)
        runCurrent()
        advanceTimeBy(interval.inWholeMilliseconds)
        runCurrent()
        secondJob.cancel()

        assertHeartbeats(sentPackets, 0, 1, 2, 3)
    }

    private fun TestScope.launchRepeatingHeartbeatLoop(sender: HeartbeatSender, interval: Duration): Job =
        backgroundScope.launch {
            while (isActive) {
                sender.sendHeartbeat()
                delay(interval)
            }
        }

    private fun TestScope.launchFiniteHeartbeatLoop(
        sender: HeartbeatSender,
        interval: Duration,
        repeatCount: Int,
    ): Job = backgroundScope.launch {
        repeat(repeatCount) {
            sender.sendHeartbeat()
            delay(interval)
        }
    }

    private fun assertHeartbeats(sentPackets: List<ByteArray>, vararg expectedNonces: Int) {
        assertEquals(expectedNonces.size, sentPackets.size)
        sentPackets.zip(expectedNonces.toList()).forEachIndexed { index, (packet, expectedNonce) ->
            val message = ToRadio.ADAPTER.decode(packet)
            val heartbeat = assertNotNull(message.heartbeat, "Missing heartbeat at index $index")
            assertEquals(expectedNonce, heartbeat.nonce, "Unexpected nonce at index $index")
        }
    }
}

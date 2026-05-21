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
package org.meshtastic.core.takserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Tests for [TAKServerManagerImpl] offline message queue behavior:
 * - FIFO eviction at 50-message cap (T074)
 * - Per-message TTL expiry after 5 minutes (T074)
 * - Replay of queued messages on client reconnect (T074)
 */
class TAKServerManagerTest {

    /** Fake TAKServer that records broadcasts and simulates connection state. */
    private class FakeTAKServer : TAKServer {
        override val connectionCount: StateFlow<Int> = MutableStateFlow(0)
        override var onMessage: ((CoTMessage, TAKClientInfo?) -> Unit)? = null
        override var onClientConnected: (() -> Unit)? = null

        var stubHasConnections = false
        val broadcasts = mutableListOf<CoTMessage>()
        val rawBroadcasts = mutableListOf<String>()

        override suspend fun start(scope: CoroutineScope): Result<Unit> = Result.success(Unit)

        override fun stop() {}

        override suspend fun broadcast(cotMessage: CoTMessage) {
            broadcasts.add(cotMessage)
        }

        override suspend fun broadcastRawXml(xml: String) {
            rawBroadcasts.add(xml)
        }

        override suspend fun hasConnections(): Boolean = stubHasConnections
    }

    private fun createPli(uid: String): CoTMessage {
        val now = Clock.System.now()
        return CoTMessage(
            uid = uid,
            type = DEFAULT_PLI_COT_TYPE,
            stale = now + 5.minutes,
            latitude = 45.0,
            longitude = -90.0,
        )
    }

    @Test
    fun `offline queue caps at 50 messages with FIFO eviction`() = runTest {
        val fakeTakServer = FakeTAKServer()
        fakeTakServer.stubHasConnections = false
        val manager = TAKServerManagerImpl(fakeTakServer)
        manager.start(this)
        advanceUntilIdle()

        // Queue 55 messages (5 more than the cap)
        repeat(55) { i -> manager.broadcast(createPli("uid-$i")) }
        advanceUntilIdle()

        // Now simulate a client connecting — drain the queue
        fakeTakServer.stubHasConnections = true
        manager.drainOfflineQueue()
        advanceUntilIdle()

        // Should have drained exactly 50 messages (the oldest 5 evicted by FIFO)
        assertEquals(50, fakeTakServer.broadcasts.size)
        // The first message drained should be uid-5 (uid-0 through uid-4 were evicted)
        assertEquals("uid-5", fakeTakServer.broadcasts.first().uid)
        // The last message drained should be uid-54
        assertEquals("uid-54", fakeTakServer.broadcasts.last().uid)

        manager.stop()
    }

    @Test
    fun `offline queue expires messages after 5 minute TTL`() = runTest {
        val fakeTakServer = FakeTAKServer()
        fakeTakServer.stubHasConnections = false
        val manager = TAKServerManagerImpl(fakeTakServer)
        manager.start(this)
        advanceUntilIdle()

        // Queue 3 messages — these will be stamped with Clock.System.now()
        repeat(3) { i -> manager.broadcast(createPli("msg-$i")) }
        advanceUntilIdle()

        // Immediately drain (no time has passed) — all messages should still be valid
        fakeTakServer.stubHasConnections = true
        manager.drainOfflineQueue()
        advanceUntilIdle()

        // All 3 messages should be delivered (none expired yet since <5 min elapsed)
        assertEquals(3, fakeTakServer.broadcasts.size)
        assertEquals("msg-0", fakeTakServer.broadcasts[0].uid)
        assertEquals("msg-1", fakeTakServer.broadcasts[1].uid)
        assertEquals("msg-2", fakeTakServer.broadcasts[2].uid)

        manager.stop()
    }

    @Test
    fun `offline queue replays messages in order on client reconnect`() = runTest {
        val fakeTakServer = FakeTAKServer()
        fakeTakServer.stubHasConnections = false
        val manager = TAKServerManagerImpl(fakeTakServer)
        manager.start(this)
        advanceUntilIdle()

        // Queue messages in order
        val uids = listOf("alpha", "bravo", "charlie", "delta")
        uids.forEach { uid -> manager.broadcast(createPli(uid)) }
        advanceUntilIdle()

        // Simulate client reconnect
        fakeTakServer.stubHasConnections = true
        manager.drainOfflineQueue()
        advanceUntilIdle()

        // Messages should be replayed in FIFO order
        assertEquals(uids, fakeTakServer.broadcasts.map { it.uid })

        manager.stop()
    }

    @Test
    fun `broadcast goes directly to TAK server when clients connected`() = runTest {
        val fakeTakServer = FakeTAKServer()
        fakeTakServer.stubHasConnections = true
        val manager = TAKServerManagerImpl(fakeTakServer)
        manager.start(this)
        advanceUntilIdle()

        manager.broadcast(createPli("direct"))
        advanceUntilIdle()

        // Message should be broadcast directly, not queued
        assertEquals(1, fakeTakServer.broadcasts.size)
        assertEquals("direct", fakeTakServer.broadcasts.first().uid)

        manager.stop()
    }

    // ── T076: Port conflict / start failure ─────────────────────────────────────

    /** Fake TAKServer that simulates a port-conflict failure on start. */
    private class FailingTAKServer : TAKServer {
        override val connectionCount: StateFlow<Int> = MutableStateFlow(0)
        override var onMessage: ((CoTMessage, TAKClientInfo?) -> Unit)? = null
        override var onClientConnected: (() -> Unit)? = null

        override suspend fun start(scope: CoroutineScope): Result<Unit> =
            Result.failure(IllegalStateException("Address already in use: port 8089"))

        override fun stop() {}

        override suspend fun broadcast(cotMessage: CoTMessage) {}

        override suspend fun broadcastRawXml(xml: String) {}

        override suspend fun hasConnections(): Boolean = false
    }

    @Test
    fun `start failure due to port conflict leaves isRunning false`() = runTest {
        val failingServer = FailingTAKServer()
        val manager = TAKServerManagerImpl(failingServer)
        manager.start(this)
        advanceUntilIdle()

        // Manager should NOT be running after start failure
        assertEquals(false, manager.isRunning.value)
    }

    @Test
    fun `start failure clears onMessage callback`() = runTest {
        val failingServer = FailingTAKServer()
        val manager = TAKServerManagerImpl(failingServer)
        manager.start(this)
        advanceUntilIdle()

        // onMessage should be cleared after failed start
        assertEquals(null, failingServer.onMessage)
    }

    @Test
    fun `broadcast is no-op after failed start`() = runTest {
        val failingServer = FailingTAKServer()
        val manager = TAKServerManagerImpl(failingServer)
        manager.start(this)
        advanceUntilIdle()

        // Broadcast should silently do nothing (not crash)
        manager.broadcast(createPli("should-be-dropped"))
        advanceUntilIdle()
        // No exception = pass. isRunning is false so broadcast exits early.
    }

    @Test
    fun `broadcastRawXml forwards to TAKServer when running`() = runTest {
        val fakeTakServer = FakeTAKServer()
        fakeTakServer.stubHasConnections = true
        val manager = TAKServerManagerImpl(fakeTakServer)
        manager.start(this)
        advanceUntilIdle()

        val rawXml = """<event type="a-f-G" uid="test"/>"""
        manager.broadcastRawXml(rawXml)
        advanceUntilIdle()

        assertEquals(1, fakeTakServer.rawBroadcasts.size)
        assertEquals(rawXml, fakeTakServer.rawBroadcasts.first())
    }

    @Test
    fun `broadcastRawXml is no-op when not running`() = runTest {
        val fakeTakServer = FakeTAKServer()
        val manager = TAKServerManagerImpl(fakeTakServer)
        // Don't call start()

        manager.broadcastRawXml("""<event type="a-f-G"/>""")
        advanceUntilIdle()

        assertTrue(fakeTakServer.rawBroadcasts.isEmpty())
    }
}

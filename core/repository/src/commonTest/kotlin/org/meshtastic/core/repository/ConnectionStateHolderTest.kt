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
package org.meshtastic.core.repository

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.model.ConnectionEpochs
import org.meshtastic.core.model.ConnectionLifecycle
import org.meshtastic.core.model.ConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ConnectionStateHolderTest {
    @Test
    fun `transitions advance matching epochs exactly once`() {
        val holder = ConnectionStateHolder()

        holder.setConnectionState(ConnectionState.Connected)
        holder.setConnectionState(ConnectionState.Connected)
        holder.setConnectionState(ConnectionState.Connecting)
        holder.setConnectionState(ConnectionState.Disconnected)
        holder.setConnectionState(ConnectionState.Connected)

        assertEquals(ConnectionState.Connected, holder.connectionState.value)
        assertEquals(4L, holder.connectionLifecycle.value.version)
        assertEquals(
            ConnectionEpochs(
                departures = 1,
                completedHandshakes = 2,
                handshakesAtLastDeparture = 1,
                lastDepartureState = ConnectionState.Connecting,
            ),
            holder.connectionEpochs.value,
        )
    }

    @Test
    fun `device sleep departure records handshake evidence`() {
        val holder = ConnectionStateHolder()

        holder.setConnectionState(ConnectionState.Connected)
        holder.setConnectionState(ConnectionState.DeviceSleep)

        assertEquals(
            ConnectionEpochs(
                departures = 1,
                completedHandshakes = 1,
                handshakesAtLastDeparture = 1,
                lastDepartureState = ConnectionState.DeviceSleep,
            ),
            holder.connectionEpochs.value,
        )
    }

    @Test
    fun `lifecycle flow publishes state and epochs as one correlated snapshot`() {
        val holder = ConnectionStateHolder()

        holder.setConnectionState(ConnectionState.Connected)
        holder.setConnectionState(ConnectionState.Connecting)

        assertEquals(
            ConnectionLifecycle(
                version = 2,
                state = ConnectionState.Connecting,
                epochs =
                ConnectionEpochs(
                    departures = 1,
                    completedHandshakes = 1,
                    handshakesAtLastDeparture = 1,
                    lastDepartureState = ConnectionState.Connecting,
                ),
            ),
            holder.connectionLifecycle.value,
        )
    }

    @Test
    fun `concurrent duplicate transitions advance each lifecycle edge once`() = runTest {
        val holder = ConnectionStateHolder()

        suspend fun fanOut(state: ConnectionState) = coroutineScope {
            List(100) { async(Dispatchers.Default) { holder.setConnectionState(state) } }.awaitAll()
        }

        fanOut(ConnectionState.Connected)
        fanOut(ConnectionState.Connecting)
        fanOut(ConnectionState.Connected)

        assertEquals(ConnectionState.Connected, holder.connectionState.value)
        assertEquals(
            ConnectionEpochs(
                departures = 1,
                completedHandshakes = 2,
                handshakesAtLastDeparture = 1,
                lastDepartureState = ConnectionState.Connecting,
            ),
            holder.connectionEpochs.value,
        )
    }

    @Test
    fun `initial epochs reject impossible departure evidence`() {
        assertFailsWith<IllegalArgumentException> {
            ConnectionStateHolder(
                initialEpochs =
                ConnectionEpochs(
                    departures = 0,
                    completedHandshakes = 1,
                    handshakesAtLastDeparture = 1,
                    lastDepartureState = null,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ConnectionStateHolder(
                initialEpochs =
                ConnectionEpochs(
                    departures = 1,
                    completedHandshakes = 1,
                    handshakesAtLastDeparture = 1,
                    lastDepartureState = ConnectionState.Connected,
                ),
            )
        }
    }

    @Test
    fun `concurrent mixed transitions publish correlated lifecycle snapshots while active`() = runTest {
        repeat(25) {
            val holder = ConnectionStateHolder()
            val snapshotsLock = SynchronizedObject()
            val snapshots = mutableListOf<ConnectionLifecycle>()
            val collectorReady = CompletableDeferred<Unit>()
            val transitionObserved = CompletableDeferred<Unit>()
            val collector =
                backgroundScope.launch(Dispatchers.Default) {
                    holder.connectionLifecycle.collect { lifecycle ->
                        synchronized(snapshotsLock) { snapshots += lifecycle }
                        collectorReady.complete(Unit)
                        if (lifecycle.version > 0) transitionObserved.complete(Unit)
                    }
                }
            collectorReady.await()

            val states =
                List(25) {
                    listOf(
                        ConnectionState.Connected,
                        ConnectionState.Connecting,
                        ConnectionState.DeviceSleep,
                        ConnectionState.Disconnected,
                    )
                }
                    .flatten()
            coroutineScope {
                states.map { state -> async(Dispatchers.Default) { holder.setConnectionState(state) } }.awaitAll()
            }
            withContext(Dispatchers.Default) { withTimeout(5.seconds) { transitionObserved.await() } }
            collector.cancelAndJoin()

            val recordedSnapshots = synchronized(snapshotsLock) { snapshots.toList() }
            assertTrue(recordedSnapshots.any { it.version > 0 }, "collector must observe an active transition")
            recordedSnapshots.forEach { lifecycle ->
                val connectedOffset = if (lifecycle.state is ConnectionState.Connected) 1 else 0
                assertEquals(lifecycle.epochs.departures + connectedOffset, lifecycle.epochs.completedHandshakes)
                assertTrue(
                    lifecycle.epochs.handshakesAtLastDeparture <= lifecycle.epochs.completedHandshakes,
                    "departure handshake evidence must come from the same lifecycle commit",
                )
            }

            val lifecycle = holder.connectionLifecycle.value
            assertEquals(lifecycle.state, holder.connectionState.value)
            assertEquals(lifecycle.epochs, holder.connectionEpochs.value)
        }
    }

    @Test
    fun `reset preserves monotonic epochs when no baseline is supplied`() {
        val holder = ConnectionStateHolder()
        holder.setConnectionState(ConnectionState.Connected)
        holder.setConnectionState(ConnectionState.Disconnected)
        val epochsBeforeReset = holder.connectionEpochs.value

        holder.reset(state = ConnectionState.DeviceSleep)

        assertEquals(epochsBeforeReset, holder.connectionEpochs.value)
        assertEquals(ConnectionState.DeviceSleep, holder.connectionState.value)
    }

    @Test
    fun `reset into disconnected records a canonical departure`() {
        val holder = ConnectionStateHolder()
        holder.setConnectionState(ConnectionState.Connected)

        holder.reset()

        assertEquals(ConnectionState.Disconnected, holder.connectionState.value)
        assertEquals(
            ConnectionEpochs(
                departures = 1,
                completedHandshakes = 1,
                handshakesAtLastDeparture = 1,
                lastDepartureState = ConnectionState.Disconnected,
            ),
            holder.connectionEpochs.value,
        )
    }

    @Test
    fun `reset rejects an explicit epoch baseline that rewinds monotonic counters`() {
        val holder = ConnectionStateHolder()
        holder.setConnectionState(ConnectionState.Connected)
        holder.setConnectionState(ConnectionState.Disconnected)

        assertFailsWith<IllegalArgumentException> { holder.reset(epochs = ConnectionEpochs()) }
    }

    @Test
    fun `explicit reset baseline must include the requested lifecycle transition`() {
        val holder = ConnectionStateHolder()
        holder.setConnectionState(ConnectionState.Connected)
        val connectedEpochs = holder.connectionEpochs.value

        assertFailsWith<IllegalArgumentException> {
            holder.reset(state = ConnectionState.Disconnected, epochs = connectedEpochs)
        }
    }

    @Test
    fun `explicit reset baseline must preserve the canonical departure state`() {
        val holder = ConnectionStateHolder()
        holder.setConnectionState(ConnectionState.Connected)

        assertFailsWith<IllegalArgumentException> {
            holder.reset(
                state = ConnectionState.Disconnected,
                epochs =
                ConnectionEpochs(
                    departures = 1,
                    completedHandshakes = 1,
                    handshakesAtLastDeparture = 1,
                    lastDepartureState = ConnectionState.Connecting,
                ),
            )
        }
    }

    @Test
    fun `explicit reset baseline must retain its last departure state`() {
        val holder = ConnectionStateHolder()

        assertFailsWith<IllegalArgumentException> { holder.reset(epochs = ConnectionEpochs(departures = 1)) }
    }

    @Test
    fun `constructor rejects negative epoch counters`() {
        listOf(
            ConnectionEpochs(departures = -1, lastDepartureState = ConnectionState.Disconnected),
            ConnectionEpochs(completedHandshakes = -1),
            ConnectionEpochs(handshakesAtLastDeparture = -1),
        )
            .forEach { epochs ->
                assertFailsWith<IllegalArgumentException> { ConnectionStateHolder(initialEpochs = epochs) }
            }
    }

    @Test
    fun `constructor rejects an initial epoch snapshot without its departure state`() {
        assertFailsWith<IllegalArgumentException> {
            ConnectionStateHolder(initialEpochs = ConnectionEpochs(departures = 1))
        }
    }

    @Test
    fun `constructor rejects initial departure evidence beyond completed handshakes`() {
        assertFailsWith<IllegalArgumentException> {
            ConnectionStateHolder(
                initialEpochs =
                ConnectionEpochs(
                    departures = 1,
                    completedHandshakes = 1,
                    handshakesAtLastDeparture = 2,
                    lastDepartureState = ConnectionState.Disconnected,
                ),
            )
        }
    }

    @Test
    fun `reset restores an explicit state and epoch baseline`() {
        val holder = ConnectionStateHolder()
        holder.setConnectionState(ConnectionState.Connected)
        val versionBeforeReset = holder.connectionLifecycle.value.version

        holder.reset(
            state = ConnectionState.DeviceSleep,
            epochs =
            ConnectionEpochs(
                departures = 7,
                completedHandshakes = 11,
                handshakesAtLastDeparture = 10,
                lastDepartureState = ConnectionState.DeviceSleep,
            ),
        )

        assertEquals(versionBeforeReset + 1, holder.connectionLifecycle.value.version)
        assertEquals(ConnectionState.DeviceSleep, holder.connectionState.value)
        assertEquals(
            ConnectionEpochs(
                departures = 7,
                completedHandshakes = 11,
                handshakesAtLastDeparture = 10,
                lastDepartureState = ConnectionState.DeviceSleep,
            ),
            holder.connectionEpochs.value,
        )
    }
}

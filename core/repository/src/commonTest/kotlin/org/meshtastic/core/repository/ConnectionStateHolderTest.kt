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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.ConnectionEpochs
import org.meshtastic.core.model.ConnectionLifecycle
import org.meshtastic.core.model.ConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `concurrent mixed transitions publish correlated lifecycle snapshots while active`() = runTest {
        repeat(25) {
            val holder = ConnectionStateHolder()
            val collectorReady = CompletableDeferred<Unit>()
            val transitionObserved = CompletableDeferred<Unit>()
            val collector =
                backgroundScope.launch(Dispatchers.Default) {
                    holder.connectionLifecycle.collect { lifecycle ->
                        collectorReady.complete(Unit)
                        if (lifecycle.version > 0) transitionObserved.complete(Unit)

                        val connectedOffset = if (lifecycle.state is ConnectionState.Connected) 1 else 0
                        assertEquals(
                            lifecycle.epochs.departures + connectedOffset,
                            lifecycle.epochs.completedHandshakes,
                        )
                        assertTrue(
                            lifecycle.epochs.handshakesAtLastDeparture <= lifecycle.epochs.completedHandshakes,
                            "departure handshake evidence must come from the same lifecycle commit",
                        )
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
            transitionObserved.await()
            collector.cancelAndJoin()

            val lifecycle = holder.connectionLifecycle.value
            assertEquals(lifecycle.state, holder.connectionState.value)
            assertEquals(lifecycle.epochs, holder.connectionEpochs.value)
        }
    }

    @Test
    fun `reset restores an explicit state and epoch baseline`() {
        val holder = ConnectionStateHolder()
        holder.setConnectionState(ConnectionState.Connected)
        val versionBeforeReset = holder.connectionLifecycle.value.version

        holder.reset(
            state = ConnectionState.DeviceSleep,
            epochs = ConnectionEpochs(departures = 7, completedHandshakes = 11, handshakesAtLastDeparture = 10),
        )

        assertEquals(versionBeforeReset + 1, holder.connectionLifecycle.value.version)
        assertEquals(ConnectionState.DeviceSleep, holder.connectionState.value)
        assertEquals(
            ConnectionEpochs(departures = 7, completedHandshakes = 11, handshakesAtLastDeparture = 10),
            holder.connectionEpochs.value,
        )
    }
}

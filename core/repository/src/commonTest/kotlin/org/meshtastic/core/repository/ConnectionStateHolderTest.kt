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
import org.meshtastic.sdk.ConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ConnectionStateHolderTest {
    @Test
    fun `lifecycle version changes when the connection drops and returns`() {
        val holder = ConnectionStateHolder()

        holder.setConnectionState(ConnectionState.Connected)
        val versionWhileConnected = holder.connectionLifecycle.value.version
        holder.setConnectionState(ConnectionState.Disconnected)
        holder.setConnectionState(ConnectionState.Connected)

        assertEquals(ConnectionState.Connected, holder.connectionState.value)
        assertEquals(versionWhileConnected + 2, holder.connectionLifecycle.value.version)
    }

    @Test
    fun `version advances once per state change and not on repeats`() {
        val holder = ConnectionStateHolder()

        holder.setConnectionState(ConnectionState.Connected)
        holder.setConnectionState(ConnectionState.Connected)
        holder.setConnectionState(ConnectionState.Connecting(attempt = 1))
        holder.setConnectionState(ConnectionState.Disconnected)
        holder.setConnectionState(ConnectionState.Connected)

        assertEquals(ConnectionState.Connected, holder.connectionState.value)
        assertEquals(4L, holder.connectionLifecycle.value.version)
    }

    @Test
    fun `lifecycle flow publishes state and version as one snapshot`() {
        val holder = ConnectionStateHolder()

        holder.setConnectionState(ConnectionState.Connected)
        holder.setConnectionState(ConnectionState.Connecting(attempt = 1))

        assertEquals(
            ConnectionLifecycle(version = 2, state = ConnectionState.Connecting(attempt = 1)),
            holder.connectionLifecycle.value,
        )
    }

    @Test
    fun `concurrent duplicate transitions advance the version once per edge`() = runTest {
        val holder = ConnectionStateHolder()

        suspend fun fanOut(state: ConnectionState) = coroutineScope {
            List(100) { async(Dispatchers.Default) { holder.setConnectionState(state) } }.awaitAll()
        }

        fanOut(ConnectionState.Connected)
        fanOut(ConnectionState.Connecting(attempt = 1))
        fanOut(ConnectionState.Connected)

        assertEquals(ConnectionState.Connected, holder.connectionState.value)
        assertEquals(3L, holder.connectionLifecycle.value.version)
    }

    @Test
    fun `concurrent mixed transitions keep lifecycle and state projections equal while active`() = runTest {
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
                        ConnectionState.Connecting(attempt = 1),
                        ConnectionState.DeviceSleeping(
                            kotlin.time.Instant.fromEpochMilliseconds(0),
                            expectedWakeSeconds = 300,
                        ),
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
            val lifecycle = holder.connectionLifecycle.value
            assertEquals(lifecycle.state, holder.connectionState.value)
        }
    }

    @Test
    fun `reset restores an explicit state and always advances the version`() {
        val holder = ConnectionStateHolder()
        holder.setConnectionState(ConnectionState.Connected)
        val versionBeforeReset = holder.connectionLifecycle.value.version
        val sleeping =
            ConnectionState.DeviceSleeping(kotlin.time.Instant.fromEpochMilliseconds(0), expectedWakeSeconds = 300)

        holder.reset(state = sleeping)

        assertEquals(versionBeforeReset + 1, holder.connectionLifecycle.value.version)
        assertEquals(sleeping, holder.connectionState.value)
        assertEquals(sleeping, holder.connectionLifecycle.value.state)

        holder.reset(state = sleeping)

        assertEquals(versionBeforeReset + 2, holder.connectionLifecycle.value.version)
    }
}

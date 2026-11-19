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
package org.meshtastic.core.network.radio

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.transport.TcpTransport
import org.meshtastic.core.repository.RadioTransportCallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TcpRadioTransportTest {

    private val callback: RadioTransportCallback = mock(MockMode.autofill)

    private class FakeTcpRadioConnection : TcpRadioConnection {
        private val connectedState = atomic(false)
        var connected: Boolean
            get() = connectedState.value
            set(value) {
                connectedState.value = value
            }

        private val sentPacketsLock = SynchronizedObject()
        private val mutableSentPackets = mutableListOf<ByteArray>()
        private val startsState = atomic(0)
        private val stopsState = atomic(0)

        val sentPackets: List<ByteArray>
            get() = synchronized(sentPacketsLock) { mutableSentPackets.toList() }

        val starts: Int
            get() = startsState.value

        val stops: Int
            get() = stopsState.value

        var stopStarted: CompletableDeferred<Unit>? = null
        var releaseStop: CompletableDeferred<Unit>? = null
        var sendStarted: CompletableDeferred<Unit>? = null
        var releaseSend: CompletableDeferred<Unit>? = null
        private val sendInFlight = atomic(false)
        private val stopObservedSendInFlightState = atomic(false)

        val stopObservedSendInFlight: Boolean
            get() = stopObservedSendInFlightState.value

        override val isConnected: Boolean
            get() = connected

        override fun start(address: String) {
            startsState.incrementAndGet()
        }

        override fun stop() {
            stopsState.incrementAndGet()
            stopObservedSendInFlightState.value = sendInFlight.value
            stopStarted?.complete(Unit)
            releaseStop?.let { gate -> runBlocking { withTimeout(5.seconds) { gate.await() } } }
            connected = false
        }

        override suspend fun sendPacket(payload: ByteArray) {
            sendInFlight.value = true
            try {
                sendStarted?.complete(Unit)
                releaseSend?.await()
                synchronized(sentPacketsLock) { mutableSentPackets += payload }
            } finally {
                sendInFlight.value = false
            }
        }

        override suspend fun sendHeartbeat() = Unit
    }

    private fun createTransport(scope: CoroutineScope, connection: FakeTcpRadioConnection): TcpRadioTransport =
        TcpRadioTransport(
            callback = callback,
            scope = scope,
            address = "127.0.0.1",
            connectionFactory = { _: TcpTransport.Listener -> connection },
        )

    @Test
    fun `send is rejected while the transport was never started`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher)
        val transport = TcpRadioTransport(callback, this, dispatchers, address = "127.0.0.1")

        assertFalse(transport.handleSendToRadio(byteArrayOf(1, 2, 3)))

        transport.close()

        assertFalse(transport.handleSendToRadio(byteArrayOf(4, 5, 6)))
    }

    @Test
    fun `established transport admits send before close and rejects after close`() = runTest {
        val connection = FakeTcpRadioConnection().apply { connected = true }
        val transport = createTransport(this, connection)
        val payload = byteArrayOf(1, 2, 3)

        assertTrue(transport.handleSendToRadio(payload))
        runCurrent()
        assertEquals(listOf(payload.toList()), connection.sentPackets.map(ByteArray::toList))

        transport.close()
        transport.close()

        assertFalse(transport.handleSendToRadio(byteArrayOf(4, 5, 6)))
        assertEquals(1, connection.stops)
    }

    @Test
    fun `close waits for an admitted send to finish before stopping the connection`() = runTest {
        val payload = byteArrayOf(4, 5, 6)
        val sendStarted = CompletableDeferred<Unit>()
        val releaseSend = CompletableDeferred<Unit>()
        val stopStarted = CompletableDeferred<Unit>()
        val connection =
            FakeTcpRadioConnection().apply {
                connected = true
                this.sendStarted = sendStarted
                this.releaseSend = releaseSend
                this.stopStarted = stopStarted
            }
        val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transport = createTransport(transportScope, connection)

        try {
            assertTrue(transport.handleSendToRadio(payload))
            sendStarted.await()

            val closeJob = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { transport.close() }
            val prematureStop =
                withContext(Dispatchers.Default) { withTimeoutOrNull(200.milliseconds) { stopStarted.await() } }
            assertNull(prematureStop, "close must not stop the connection while admitted TCP I/O is suspended")

            releaseSend.complete(Unit)
            withContext(Dispatchers.Default) { withTimeout(5.seconds) { stopStarted.await() } }

            assertEquals(
                listOf(payload.toList()),
                connection.sentPackets.map(ByteArray::toList),
                "the admitted send must finish before connection teardown starts",
            )
            assertFalse(connection.stopObservedSendInFlight)
            closeJob.await()
            assertEquals(1, connection.stops)
        } finally {
            releaseSend.complete(Unit)
            transport.close()
            transportScope.cancel()
        }
    }

    @Test
    fun `hung admitted send times out before close stops the connection`() = runTest {
        val sendStarted = CompletableDeferred<Unit>()
        val releaseSend = CompletableDeferred<Unit>()
        val connection =
            FakeTcpRadioConnection().apply {
                connected = true
                this.sendStarted = sendStarted
                this.releaseSend = releaseSend
            }
        val transport = createTransport(this, connection)

        try {
            assertTrue(transport.handleSendToRadio(byteArrayOf(1, 2, 3)))
            runCurrent()
            sendStarted.await()

            val closeJob = async { transport.close() }
            runCurrent()
            advanceTimeBy(TcpRadioTransport.OPERATION_TIMEOUT.inWholeMilliseconds)
            runCurrent()
            closeJob.await()

            assertEquals(1, connection.stops)
            assertFalse(connection.stopObservedSendInFlight)
            assertTrue(connection.sentPackets.isEmpty())
        } finally {
            releaseSend.complete(Unit)
        }
    }

    @Test
    fun `operation timeout stops a connection before another send can be admitted`() = runTest {
        val sendStarted = CompletableDeferred<Unit>()
        val releaseSend = CompletableDeferred<Unit>()
        val connection =
            FakeTcpRadioConnection().apply {
                connected = true
                this.sendStarted = sendStarted
                this.releaseSend = releaseSend
            }
        val transport = createTransport(this, connection)

        try {
            transport.start()
            assertEquals(1, connection.starts)
            assertTrue(transport.handleSendToRadio(byteArrayOf(1, 2, 3)))
            runCurrent()
            sendStarted.await()

            advanceTimeBy(TcpRadioTransport.OPERATION_TIMEOUT.inWholeMilliseconds)
            runCurrent()

            assertEquals(1, connection.stops)
            transport.start()
            assertEquals(1, connection.starts, "a stopped TCP transport must not be restarted in place")
            assertFalse(transport.handleSendToRadio(byteArrayOf(4, 5, 6)))
        } finally {
            releaseSend.complete(Unit)
            transport.close()
        }
    }

    @Test
    fun `close racing with send wins admission before teardown can return`() = runTest {
        val stopStarted = CompletableDeferred<Unit>()
        val releaseStop = CompletableDeferred<Unit>()
        val connection =
            FakeTcpRadioConnection().apply {
                connected = true
                this.stopStarted = stopStarted
                this.releaseStop = releaseStop
            }
        val transport = createTransport(this, connection)

        // Both close and send use real threads here: close blocks inside the fake stop callback while the second
        // Default worker verifies that lifecycle admission has already closed.
        val closeJob = async(Dispatchers.Default) { transport.close() }
        try {
            stopStarted.await()
            val sendJob = async(Dispatchers.Default) { transport.handleSendToRadio(byteArrayOf(7, 8, 9)) }

            assertFalse(sendJob.await(), "send must be rejected while the first close is still draining")
        } finally {
            releaseStop.complete(Unit)
        }
        closeJob.await()

        assertTrue(connection.sentPackets.isEmpty())
    }
}

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

import com.hoho.android.usbserial.driver.UsbSerialDriver
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.network.repository.SerialConnection
import org.meshtastic.core.network.repository.SerialConnectionListener
import org.meshtastic.core.repository.RadioTransportCallback
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SerialRadioTransportTest {

    private val callback: RadioTransportCallback = mock(MockMode.autofill)
    private val serialDriver: UsbSerialDriver = mock(MockMode.autofill)
    private val serialConnection: SerialConnection = mock(MockMode.autofill)
    private lateinit var serialConnectionListener: SerialConnectionListener
    private var requestedDriver: UsbSerialDriver? = null

    private fun createTransport(address: String, scope: CoroutineScope): SerialRadioTransport {
        requestedDriver = null
        return SerialRadioTransport(
            callback = callback,
            scope = scope,
            serialDevices = MutableStateFlow(mapOf(address to serialDriver)),
            createSerialConnection = { driver, listener ->
                requestedDriver = driver
                serialConnectionListener = listener
                serialConnection
            },
            address = address,
        )
    }

    private fun createRecordingTransport(
        address: String,
        connections: MutableList<RecordingSerialConnection>,
        scope: CoroutineScope,
        devices: Map<String, UsbSerialDriver> = mapOf(address to serialDriver),
    ): SerialRadioTransport {
        requestedDriver = null
        return SerialRadioTransport(
            callback = callback,
            scope = scope,
            serialDevices = MutableStateFlow(devices),
            createSerialConnection = { driver, listener ->
                requestedDriver = driver
                RecordingSerialConnection(listener).also(connections::add)
            },
            address = address,
        )
    }

    private fun assertRequestedDriver(expected: UsbSerialDriver = serialDriver) {
        assertSame(expected, requestedDriver, "serial connection factory must receive the selected driver")
    }

    @Test
    fun `failed connection is not left admitted`() = runTest {
        val address = "serial-device"
        every { serialConnection.connect() } throws IllegalStateException("connect failed")
        val transport = createTransport(address, backgroundScope)

        transport.start()
        testScheduler.runCurrent()
        assertRequestedDriver()

        verify(exactly(1)) { serialConnection.close(waitForStopped = false) }
        verify(exactly(1)) {
            callback.onDisconnect(isPermanent = false, errorMessage = "connect failed", reason = null)
        }
        assertFalse(transport.handleSendToRadio(byteArrayOf(1)))
        transport.close()
    }

    @Test
    fun `send is rejected promptly while connection is still opening`() = runBlocking<Unit> {
        val address = "serial-device"
        val connectEntered = CountDownLatch(1)
        val releaseConnect = CountDownLatch(1)
        every { serialConnection.connect() } calls
            {
                connectEntered.countDown()
                check(releaseConnect.await(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS))
                serialConnectionListener.onConnected()
            }
        val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transport = createTransport(address, transportScope)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val startFuture = executor.submit { transport.start() }
            assertTrue(
                connectEntered.await(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS),
                "connect should enter the blocking open",
            )

            val sendFuture = executor.submit<Boolean> { transport.handleSendToRadio(byteArrayOf(1)) }
            assertFalse(
                sendFuture.get(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS),
                "an opening transport must reject promptly",
            )

            releaseConnect.countDown()
            startFuture.get(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS)
            assertRequestedDriver()
        } finally {
            releaseConnect.countDown()
            transport.close()
            transportScope.cancel()
            executor.shutdownNow()
        }
    }

    @Test
    fun `close orders against connection creation before publication`() = runBlocking<Unit> {
        val address = "serial-device"
        val creationEntered = CountDownLatch(1)
        val releaseCreation = CountDownLatch(1)
        val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var factoryDriver: UsbSerialDriver? = null
        val transport =
            SerialRadioTransport(
                callback = callback,
                scope = transportScope,
                serialDevices = MutableStateFlow(mapOf(address to serialDriver)),
                createSerialConnection = { driver, listener ->
                    factoryDriver = driver
                    serialConnectionListener = listener
                    creationEntered.countDown()
                    check(releaseCreation.await(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS))
                    serialConnection
                },
                address = address,
            )
        val executor = Executors.newFixedThreadPool(2)

        try {
            val startFuture = executor.submit { transport.start() }
            assertTrue(creationEntered.await(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS))

            val closeFuture = executor.submit { runBlocking { transport.close() } }
            assertFailsWith<TimeoutException> { closeFuture.get(NOT_YET_WINDOW_MS, TimeUnit.MILLISECONDS) }

            releaseCreation.countDown()
            startFuture.get(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS)
            closeFuture.get(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS)

            assertSame(serialDriver, factoryDriver, "serial connection factory must receive the selected driver")
            verify(exactly(0)) { serialConnection.connect() }
            verify(exactly(1)) { serialConnection.close(waitForStopped = false) }
        } finally {
            releaseCreation.countDown()
            transport.close()
            transportScope.cancel()
            executor.shutdownNow()
        }
    }

    @Test
    fun `close waits for wake IO then revokes without publishing connected`() = runBlocking<Unit> {
        val address = "serial-device"
        val wakeWriteEntered = CountDownLatch(1)
        val releaseWakeWrite = CountDownLatch(1)
        val closeEntered = CountDownLatch(1)
        every { serialConnection.connect() } calls { serialConnectionListener.onConnected() }
        every { serialConnection.sendBytes(any()) } calls
            {
                wakeWriteEntered.countDown()
                check(releaseWakeWrite.await(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS))
            }
        every { serialConnection.close(waitForStopped = true) } calls { closeEntered.countDown() }
        val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transport = createTransport(address, transportScope)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val startFuture = executor.submit { transport.start() }
            assertTrue(
                wakeWriteEntered.await(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS),
                "connected callback should enter wake I/O",
            )
            assertFalse(
                transport.handleSendToRadio(byteArrayOf(1)),
                "ordinary writes must remain closed until wake I/O completes",
            )

            val closeFuture = executor.submit { runBlocking { transport.close() } }
            assertFalse(
                closeEntered.await(NOT_YET_WINDOW_MS, TimeUnit.MILLISECONDS),
                "close must not close the connection while admitted wake I/O is still running",
            )

            releaseWakeWrite.countDown()
            startFuture.get(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS)
            assertRequestedDriver()
            closeFuture.get(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS)
            assertTrue(closeEntered.await(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS))
            verify(exactly(0)) { callback.onConnect() }
        } finally {
            releaseWakeWrite.countDown()
            transport.close()
            transportScope.cancel()
            executor.shutdownNow()
        }
    }

    @Test
    fun `unexpected wake failure closes the connection without publishing connected`() = runTest {
        val address = "serial-device"
        every { serialConnection.connect() } calls { serialConnectionListener.onConnected() }
        every { serialConnection.sendBytes(any()) } throws UnsupportedOperationException("wake failed")
        val transport = createTransport(address, backgroundScope)

        transport.start()
        testScheduler.runCurrent()
        assertRequestedDriver()

        verify(exactly(0)) { callback.onConnect() }
        verify(exactly(1)) { callback.onDisconnect(isPermanent = false, errorMessage = null, reason = null) }
        verify(exactly(1)) { serialConnection.close(waitForStopped = false) }
        assertFalse(transport.handleSendToRadio(byteArrayOf(1)))
        transport.close()
    }

    @Test
    fun `explicit close suppresses a disconnect notification deferred by admitted IO`() = runTest {
        val address = "serial-device"
        every { serialConnection.connect() } calls { serialConnectionListener.onConnected() }
        val transport = createTransport(address, this)

        transport.start()
        assertRequestedDriver()
        assertTrue(transport.handleSendToRadio(byteArrayOf(1)))
        serialConnectionListener.onDisconnected(null)
        transport.close()
        testScheduler.runCurrent()

        verify(exactly(0)) { callback.onDisconnect(isPermanent = false, errorMessage = null, reason = null) }
        verify(exactly(1)) { serialConnection.close(waitForStopped = false) }
    }

    @Test
    fun `close drains an admitted queued write before closing its connection`() = runBlocking<Unit> {
        val address = "serial-device"
        val writeEntered = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val closeEntered = CountDownLatch(1)
        every { serialConnection.connect() } calls { serialConnectionListener.onConnected() }
        val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transport = createTransport(address, transportScope)
        val executor = Executors.newSingleThreadExecutor()

        transport.start()
        assertRequestedDriver()
        every { serialConnection.sendBytes(any()) } calls
            {
                writeEntered.countDown()
                check(releaseWrite.await(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS))
            }
        every { serialConnection.close(waitForStopped = true) } calls { closeEntered.countDown() }

        try {
            assertTrue(transport.handleSendToRadio(byteArrayOf(1, 2, 3)))
            assertTrue(
                writeEntered.await(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS),
                "the admitted write should reach the connection",
            )

            val closeFuture = executor.submit { runBlocking { transport.close() } }
            assertFalse(
                closeEntered.await(NOT_YET_WINDOW_MS, TimeUnit.MILLISECONDS),
                "close must wait for the admitted write handoff",
            )

            releaseWrite.countDown()
            closeFuture.get(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS)
            assertTrue(closeEntered.await(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS))
        } finally {
            releaseWrite.countDown()
            transport.close()
            transportScope.cancel()
            executor.shutdownNow()
        }
    }

    @Test
    fun `stale terminal callbacks do not tear down a replacement connection`() = runTest {
        val address = "serial-device"
        val connections = mutableListOf<RecordingSerialConnection>()
        val transport = createRecordingTransport(address, connections, this)

        transport.start()
        assertRequestedDriver()
        assertTrue(transport.handleSendToRadio(byteArrayOf(1)))
        connections[0].listener.onDisconnected(null)
        assertFalse(transport.handleSendToRadio(byteArrayOf(2)))
        testScheduler.runCurrent()
        verify(exactly(1)) { callback.onDisconnect(isPermanent = false, errorMessage = null, reason = null) }
        assertEquals(listOf(false), connections[0].closeRequests)

        transport.start()
        assertTrue(transport.handleSendToRadio(byteArrayOf(3)))
        connections[0].listener.onDisconnected(IllegalStateException("late disconnect"))
        assertTrue(transport.handleSendToRadio(byteArrayOf(4)))
        connections[0].listener.onMissingPermission()
        assertTrue(transport.handleSendToRadio(byteArrayOf(5)))

        testScheduler.runCurrent()
        // Mokkery verifies only calls not consumed by the earlier assertion, so stale callbacks add zero new events.
        verify(exactly(0)) { callback.onDisconnect(isPermanent = false, errorMessage = null, reason = null) }
        assertEquals(listOf(false), connections[0].closeRequests)
        assertTrue(connections[1].closeRequests.isEmpty(), "stale callbacks must not close the replacement")
        transport.close()
        assertEquals(listOf(true), connections[1].closeRequests)
    }

    @Test
    fun `start during connection cleanup opens a replacement after cleanup completes`() = runTest {
        val address = "serial-device"
        val connections = mutableListOf<RecordingSerialConnection>()
        val transport = createRecordingTransport(address, connections, this)

        transport.start()
        assertRequestedDriver()
        assertTrue(transport.handleSendToRadio(byteArrayOf(1)))
        connections.single().listener.onDisconnected(null)

        transport.start()
        assertEquals(1, connections.size, "replacement must wait for physical cleanup")
        testScheduler.runCurrent()

        assertEquals(2, connections.size)
        assertTrue(transport.handleSendToRadio(byteArrayOf(2)))
        transport.close()
        assertEquals(listOf(true), connections[1].closeRequests)
    }

    @Test
    fun `repeated start cannot replace an active connection generation`() = runTest {
        val address = "serial-device"
        val connections = mutableListOf<RecordingSerialConnection>()
        val transport = createRecordingTransport(address, connections, this)

        transport.start()
        assertRequestedDriver()
        assertTrue(transport.handleSendToRadio(byteArrayOf(1)))
        transport.start()

        assertEquals(1, connections.size, "repeated start must not open a throwaway USB generation")
        assertEquals(emptyList(), connections[0].closeRequests)
        assertTrue(transport.handleSendToRadio(byteArrayOf(2)))
        transport.close()
        assertEquals(listOf(true), connections[0].closeRequests)
    }

    @Test
    fun `concurrent close callers await the same physical teardown`() = runBlocking<Unit> {
        val address = "serial-device"
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        every { serialConnection.connect() } calls { serialConnectionListener.onConnected() }
        every { serialConnection.close(waitForStopped = true) } calls
            {
                closeEntered.countDown()
                check(releaseClose.await(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS))
            }
        val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transport = createTransport(address, transportScope)
        val executor = Executors.newFixedThreadPool(2)

        try {
            transport.start()
            assertRequestedDriver()
            val first = executor.submit { runBlocking { transport.close() } }
            assertTrue(closeEntered.await(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS))
            val second = executor.submit { runBlocking { transport.close() } }
            assertFailsWith<TimeoutException> { second.get(NOT_YET_WINDOW_MS, TimeUnit.MILLISECONDS) }

            releaseClose.countDown()
            first.get(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS)
            second.get(COMPLETION_WINDOW_SECONDS, TimeUnit.SECONDS)
            verify(exactly(1)) { serialConnection.close(waitForStopped = true) }
        } finally {
            releaseClose.countDown()
            transportScope.cancel()
            executor.shutdownNow()
        }
    }

    @Test
    fun `legacy selected address resolves the sole USB serial device`() = runTest {
        val selectedAddress = "legacy-path-address"
        val soleDevice = mock<UsbSerialDriver>()
        val connections = mutableListOf<RecordingSerialConnection>()
        val transport =
            createRecordingTransport(
                address = selectedAddress,
                connections = connections,
                scope = backgroundScope,
                devices = mapOf("stable-usb-key" to soleDevice),
            )

        transport.start()
        testScheduler.runCurrent()
        assertRequestedDriver(soleDevice)

        assertEquals(
            1,
            connections.size,
            "a legacy address may self-heal when exactly one USB serial device is present",
        )
        transport.close()
    }

    @Test
    fun `ambiguous missing selected device never falls back to another USB serial device`() = runTest {
        val selectedAddress = "selected-device"
        val connections = mutableListOf<RecordingSerialConnection>()
        val transport =
            createRecordingTransport(
                address = selectedAddress,
                connections = connections,
                scope = backgroundScope,
                devices = mapOf("other-a" to mock<UsbSerialDriver>(), "other-b" to mock<UsbSerialDriver>()),
            )

        transport.start()
        testScheduler.runCurrent()

        assertTrue(connections.isEmpty(), "an ambiguous selected-address miss must not open an unrelated serial device")
        assertFalse(transport.handleSendToRadio(byteArrayOf(1)))
        transport.close()
    }

    @Test
    fun `start after close cannot reopen a serial transport`() = runTest {
        val address = "serial-device"
        every { serialConnection.connect() } calls { serialConnectionListener.onConnected() }
        val transport = createTransport(address, this)

        transport.start()
        assertRequestedDriver()
        transport.close()
        transport.start()

        verify(exactly(1)) { serialConnection.connect() }
        verify(exactly(1)) { serialConnection.close(waitForStopped = true) }
        assertFalse(transport.handleSendToRadio(byteArrayOf(1)))
    }

    @Test
    fun `send is rejected before connection and after close`() = runTest {
        val address = "serial-device"
        val transport = createTransport(address, this)

        assertFalse(transport.handleSendToRadio(byteArrayOf(1)))

        every { serialConnection.connect() } calls { serialConnectionListener.onConnected() }
        transport.start()
        assertRequestedDriver()
        assertTrue(transport.handleSendToRadio(byteArrayOf(2)), "an established connection must accept the handoff")
        transport.close()

        verify(exactly(1)) { serialConnection.close(waitForStopped = true) }
        assertFalse(transport.handleSendToRadio(byteArrayOf(3)))
    }

    private class RecordingSerialConnection(val listener: SerialConnectionListener) : SerialConnection {
        val closeRequests = mutableListOf<Boolean>()

        override fun connect() = listener.onConnected()

        override fun sendBytes(bytes: ByteArray) = Unit

        override fun close(waitForStopped: Boolean) {
            closeRequests += waitForStopped
        }

        override fun close() = close(waitForStopped = false)
    }

    private companion object {
        // Negative wall-clock assertions get a generous scheduler margin without slowing successful paths.
        const val NOT_YET_WINDOW_MS = 500L

        // Positive waits tolerate contended CI scheduling; exceeding this bound indicates a real hang.
        const val COMPLETION_WINDOW_SECONDS = 10L
    }
}

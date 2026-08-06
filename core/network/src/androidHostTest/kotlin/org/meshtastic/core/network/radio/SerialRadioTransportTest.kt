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
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.network.repository.SerialConnection
import org.meshtastic.core.network.repository.SerialConnectionListener
import org.meshtastic.core.repository.RadioTransportCallback
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SerialRadioTransportTest {

    private val callback: RadioTransportCallback = mock(MockMode.autofill)
    private val serialDriver: UsbSerialDriver = mock(MockMode.autofill)
    private val serialConnection: SerialConnection = mock(MockMode.autofill)
    private lateinit var serialConnectionListener: SerialConnectionListener

    private fun createTransport(address: String, scope: CoroutineScope): SerialRadioTransport = SerialRadioTransport(
        callback = callback,
        scope = scope,
        serialDevices = MutableStateFlow(mapOf(address to serialDriver)),
        createSerialConnection = { driver, listener ->
            assertSame(serialDriver, driver)
            serialConnectionListener = listener
            serialConnection
        },
        address = address,
    )

    @Test
    fun `failed connection is not left admitted`() = runTest {
        val address = "serial-device"
        every { serialConnection.connect() } throws IllegalStateException("connect failed")
        val transport = createTransport(address, this)

        assertFailsWith<IllegalStateException> { transport.start() }

        verify { serialConnection.close(waitForStopped = false) }
        assertFalse(transport.handleSendToRadio(byteArrayOf(1)))
    }

    @Test
    fun `send is rejected promptly while connection is still opening`() = runTest {
        val address = "serial-device"
        val connectEntered = CountDownLatch(1)
        val releaseConnect = CountDownLatch(1)
        every { serialConnection.connect() } calls
            {
                connectEntered.countDown()
                check(releaseConnect.await(5, TimeUnit.SECONDS))
                serialConnectionListener.onConnected()
            }
        val transport = createTransport(address, this)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val startFuture = executor.submit { transport.start() }
            assertTrue(connectEntered.await(1, TimeUnit.SECONDS), "connect should enter the blocking open")

            val sendFuture = executor.submit<Boolean> { transport.handleSendToRadio(byteArrayOf(1)) }
            assertFalse(sendFuture.get(1, TimeUnit.SECONDS), "an opening transport must reject promptly")

            releaseConnect.countDown()
            startFuture.get(1, TimeUnit.SECONDS)
            transport.close()
        } finally {
            releaseConnect.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `send is rejected before connection and after disconnect`() = runTest {
        val address = "serial-device"
        val transport = createTransport(address, this)

        assertFalse(transport.handleSendToRadio(byteArrayOf(1)))

        every { serialConnection.connect() } calls { serialConnectionListener.onConnected() }
        transport.start()
        assertTrue(transport.handleSendToRadio(byteArrayOf(2)), "an established connection must accept the handoff")
        transport.close()

        verify { serialConnection.close(waitForStopped = true) }
        assertFalse(transport.handleSendToRadio(byteArrayOf(3)))
    }
}

/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.firmware.ota

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.KableBleService
import com.juul.kable.Peripheral

@OptIn(ExperimentalCoroutinesApi::class)
class BleOtaTransportTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val scanner: BleScanner = mockk()
    private val connectionFactory: BleConnectionFactory = mockk()
    private val connection: BleConnection = mockk()
    private val address = "00:11:22:33:44:55"

    private lateinit var transport: BleOtaTransport

    @Before
    fun setup() {
        every { connectionFactory.create(any(), any()) } returns connection
        every { connection.connectionState } returns MutableSharedFlow(replay = 1)
        
        transport = BleOtaTransport(
            scanner = scanner,
            connectionFactory = connectionFactory,
            address = address,
            dispatcher = testDispatcher
        )
    }

    @Test
    fun `connect throws when device not found`() = runTest(testDispatcher) {
        every { scanner.scan(any(), any()) } returns flowOf()
        
        val result = transport.connect()
        assertTrue("Expected failure", result.isFailure)
        assertTrue(result.exceptionOrNull() is OtaProtocolException.ConnectionFailed)
    }

    @Test
    fun `connect fails when connection state is disconnected`() = runTest(testDispatcher) {
        val device: BleDevice = mockk()
        every { device.address } returns address
        every { device.name } returns "Test Device"
        
        every { scanner.scan(any(), any()) } returns flowOf(device)
        coEvery { connection.connectAndAwait(any(), any(), any()) } returns BleConnectionState.Disconnected
        
        val result = transport.connect()
        assertTrue("Expected failure", result.isFailure)
        assertTrue(result.exceptionOrNull() is OtaProtocolException.ConnectionFailed)
    }
}

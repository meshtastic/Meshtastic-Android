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
package org.meshtastic.core.network.radio

import dev.mokkery.MockMode
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.ble.BluetoothState
import org.meshtastic.core.repository.RadioInterfaceService

@OptIn(ExperimentalCoroutinesApi::class)
class BleRadioInterfaceTest {

    private val testScope = TestScope()
    private val scanner: BleScanner = mock()
    private val bluetoothRepository: BluetoothRepository = mock()
    private val connectionFactory: BleConnectionFactory = mock()
    private val connection: BleConnection = mock()
    private val service: RadioInterfaceService = mock(MockMode.autofill)
    private val address = "00:11:22:33:44:55"

    private val connectionStateFlow = MutableSharedFlow<BleConnectionState>(replay = 1)
    private val bluetoothStateFlow = MutableStateFlow(BluetoothState())

    @Before
    fun setUp() {
        every { connectionFactory.create(any(), any()) } returns connection
        every { connection.connectionState } returns connectionStateFlow
        every { bluetoothRepository.state } returns bluetoothStateFlow.asStateFlow()

        bluetoothStateFlow.value = BluetoothState(enabled = true, hasPermissions = true)
    }

    @Test
    fun `connect attempts to scan and connect via init`() = runTest {
        val device: BleDevice = mock()
        every { device.address } returns address
        every { device.name } returns "Test Device"

        every { scanner.scan(any(), any()) } returns flowOf(device)
        everySuspend { connection.connectAndAwait(any(), any(), any()) } returns BleConnectionState.Connected

        val bleInterface =
            BleRadioInterface(
                serviceScope = testScope,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                service = service,
                address = address,
            )

        // init starts connect() which is async
        // We can wait for the coEvery to be triggered if needed,
        // but for a basic test this confirms it doesn't crash on init.
    }

    @Test
    fun `address returns correct value`() {
        val bleInterface =
            BleRadioInterface(
                serviceScope = testScope,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                service = service,
                address = address,
            )
        assertEquals(address, bleInterface.address)
    }
}

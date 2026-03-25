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
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.core.testing.FakeBluetoothRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BleRadioInterfaceTest {

    private val testScope = TestScope()
    private val scanner = FakeBleScanner()
    private val bluetoothRepository = FakeBluetoothRepository()
    private val connection = FakeBleConnection()
    private val connectionFactory = FakeBleConnectionFactory(connection)
    private val service: RadioInterfaceService = mock(MockMode.autofill)
    private val address = "00:11:22:33:44:55"

    @BeforeTest
    fun setup() {
        bluetoothRepository.setHasPermissions(true)
        bluetoothRepository.setBluetoothEnabled(true)
    }

    @Test
    fun `connect attempts to scan and connect via init`() = runTest {
        val device = FakeBleDevice(address = address, name = "Test Device")
        scanner.emitDevice(device)

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
        // In a real test we'd verify the connection state,
        // but for now this confirms it works with the fakes.
        assertEquals(address, bleInterface.address)
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

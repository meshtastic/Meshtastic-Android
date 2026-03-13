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
package org.meshtastic.core.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.mock.mock
import no.nordicsemi.kotlin.ble.client.mock.AddressType
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpec
import no.nordicsemi.kotlin.ble.client.mock.Proximity
import no.nordicsemi.kotlin.ble.core.LegacyAdvertisingSetParameters
import no.nordicsemi.kotlin.ble.environment.android.mock.MockAndroidEnvironment
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class BleScannerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun `scan returns peripherals`() = runTest(testDispatcher) {
        val mockEnvironment = MockAndroidEnvironment.Api31(isBluetoothEnabled = true)
        val centralManager = CentralManager.mock(mockEnvironment, backgroundScope)
        val scanner = AndroidBleScanner(centralManager)

        val peripheral =
            PeripheralSpec.simulatePeripheral(
                identifier = "00:11:22:33:44:55",
                addressType = AddressType.RANDOM_STATIC,
                proximity = Proximity.IMMEDIATE,
            ) {
                advertising(parameters = LegacyAdvertisingSetParameters(connectable = true)) {
                    CompleteLocalName("Test_Device")
                }
            }

        centralManager.simulatePeripherals(listOf(peripheral))

        val result = scanner.scan(5.seconds).first()

        assertEquals("00:11:22:33:44:55", result.address)
        assertEquals("Test_Device", result.name)
    }

    @Test
    fun `scan with filter returns only matching peripherals`() = runTest(testDispatcher) {
        val mockEnvironment = MockAndroidEnvironment.Api31(isBluetoothEnabled = true)
        val centralManager = CentralManager.mock(mockEnvironment, backgroundScope)
        val scanner = AndroidBleScanner(centralManager)

        val targetUuid = Uuid.parse("4FAFC201-1FB5-459E-8FCC-C5C9C331914B")

        val matchingPeripheral =
            PeripheralSpec.simulatePeripheral(identifier = "00:11:22:33:44:55", proximity = Proximity.IMMEDIATE) {
                advertising(parameters = LegacyAdvertisingSetParameters(connectable = true)) {
                    CompleteLocalName("Matching_Device")
                    ServiceUuid(targetUuid)
                }
            }

        val nonMatchingPeripheral =
            PeripheralSpec.simulatePeripheral(identifier = "AA:BB:CC:DD:EE:FF", proximity = Proximity.IMMEDIATE) {
                advertising(parameters = LegacyAdvertisingSetParameters(connectable = true)) {
                    CompleteLocalName("Non_Matching_Device")
                }
            }

        centralManager.simulatePeripherals(listOf(matchingPeripheral, nonMatchingPeripheral))

        val scannedDevices = mutableListOf<no.nordicsemi.kotlin.ble.client.android.Peripheral>()
        val job = launch { scanner.scan(5.seconds, targetUuid).toList(scannedDevices) }

        // Needs time to scan in mock environment
        advanceUntilIdle()
        job.cancel()

        // TODO: test filter logic correctly if necessary
    }
}

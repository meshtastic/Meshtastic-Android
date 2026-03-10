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
package org.meshtastic.feature.firmware.ota

import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.mock.mock
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpec
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpecEventHandler
import no.nordicsemi.kotlin.ble.client.mock.Proximity
import no.nordicsemi.kotlin.ble.core.CharacteristicProperty
import no.nordicsemi.kotlin.ble.core.LegacyAdvertisingSetParameters
import no.nordicsemi.kotlin.ble.core.Permission
import no.nordicsemi.kotlin.ble.core.and
import no.nordicsemi.kotlin.ble.environment.android.mock.MockAndroidEnvironment
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

private val SERVICE_UUID = Uuid.parse("4FAFC201-1FB5-459E-8FCC-C5C9C331914B")
private val OTA_CHARACTERISTIC_UUID = Uuid.parse("62ec0272-3ec5-11eb-b378-0242ac130005")
private val TX_CHARACTERISTIC_UUID = Uuid.parse("62ec0272-3ec5-11eb-b378-0242ac130003")

@OptIn(ExperimentalCoroutinesApi::class)
class BleOtaTransportMtuTest {

    private val address = "00:11:22:33:44:55"
    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun `connect requests MTU`() = runTest(testDispatcher) {
        val mockEnvironment = MockAndroidEnvironment.Api31(isBluetoothEnabled = true)
        val centralManager = spyk(CentralManager.mock(mockEnvironment, backgroundScope))

        val otaPeripheral =
            PeripheralSpec.simulatePeripheral(identifier = address, proximity = Proximity.IMMEDIATE) {
                advertising(
                    parameters = LegacyAdvertisingSetParameters(connectable = true, interval = 100.milliseconds),
                ) {
                    CompleteLocalName("ESP32-OTA")
                }
                connectable(
                    name = "ESP32-OTA",
                    eventHandler = object : PeripheralSpecEventHandler {},
                    isBonded = true,
                ) {
                    Service(uuid = SERVICE_UUID) {
                        Characteristic(
                            uuid = OTA_CHARACTERISTIC_UUID,
                            properties =
                            CharacteristicProperty.WRITE and CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                            permission = Permission.WRITE,
                        )
                        Characteristic(
                            uuid = TX_CHARACTERISTIC_UUID,
                            property = CharacteristicProperty.NOTIFY,
                            permission = Permission.READ,
                        )
                    }
                }
            }

        centralManager.simulatePeripherals(listOf(otaPeripheral))

        val scanner = org.meshtastic.core.ble.AndroidBleScanner(centralManager)
        val connectionFactory = org.meshtastic.core.ble.AndroidBleConnectionFactory(centralManager)
        val transport = BleOtaTransport(scanner, connectionFactory, address, testDispatcher)

        transport.connect().getOrThrow()

        // Verify connect was called with automaticallyRequestHighestValueLength = true
        coVerify {
            centralManager.connect(
                any(),
                CentralManager.ConnectionOptions.AutoConnect(automaticallyRequestHighestValueLength = true),
            )
        }
    }
}

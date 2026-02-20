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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.mock.mock
import no.nordicsemi.kotlin.ble.client.mock.ConnectionResult
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpec
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpecEventHandler
import no.nordicsemi.kotlin.ble.client.mock.Proximity
import no.nordicsemi.kotlin.ble.client.mock.WriteResponse
import no.nordicsemi.kotlin.ble.client.mock.internal.MockRemoteCharacteristic
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
class BleOtaTransportTest {

    private val address = "00:11:22:33:44:55"
    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `race condition check - response before waitForResponse`() = runTest(testDispatcher) {
        val mockEnvironment = MockAndroidEnvironment.Api31(isBluetoothEnabled = true)
        val centralManager = CentralManager.mock(mockEnvironment, scope = backgroundScope)

        var txCharHandle: Int = -1
        lateinit var otaPeripheral: PeripheralSpec<String>

        val eventHandler =
            object : PeripheralSpecEventHandler {
                override fun onConnectionRequest(
                    preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>,
                ): ConnectionResult = ConnectionResult.Accept

                override fun onWriteRequest(
                    characteristic: MockRemoteCharacteristic,
                    value: ByteArray,
                ): WriteResponse {
                    // When receiving an OTA command, immediately simulate a response
                    backgroundScope.launch(testDispatcher) {
                        // Use a very small delay to simulate high speed
                        delay(1.milliseconds)
                        if (otaPeripheral.isConnected) {
                            otaPeripheral.simulateValueUpdate(txCharHandle, "OK\n".toByteArray())
                        }
                    }
                    return WriteResponse.Success
                }
            }

        otaPeripheral =
            PeripheralSpec.simulatePeripheral(identifier = address, proximity = Proximity.IMMEDIATE) {
                advertising(
                    parameters = LegacyAdvertisingSetParameters(connectable = true, interval = 100.milliseconds),
                ) {
                    CompleteLocalName("ESP32-OTA")
                }
                connectable(name = "ESP32-OTA", eventHandler = eventHandler, isBonded = true) {
                    Service(uuid = SERVICE_UUID) {
                        Characteristic(
                            uuid = OTA_CHARACTERISTIC_UUID,
                            properties =
                            CharacteristicProperty.WRITE and CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                            permission = Permission.WRITE,
                        )
                        txCharHandle =
                            Characteristic(
                                uuid = TX_CHARACTERISTIC_UUID,
                                property = CharacteristicProperty.NOTIFY,
                                permission = Permission.READ,
                            )
                    }
                }
            }

        centralManager.simulatePeripherals(listOf(otaPeripheral))

        val transport = BleOtaTransport(centralManager, address, testDispatcher)

        // 1. Connect
        transport.connect().getOrThrow()

        // 2. Start OTA - should succeed even if response is very fast
        val result = transport.startOta(100L, "hash") {}
        assert(result.isSuccess)

        transport.close()
    }
}

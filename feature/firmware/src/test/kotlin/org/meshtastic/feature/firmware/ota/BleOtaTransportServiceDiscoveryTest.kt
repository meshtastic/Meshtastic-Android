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

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.mock.mock
import no.nordicsemi.kotlin.ble.client.mock.ConnectionResult
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpec
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpecEventHandler
import no.nordicsemi.kotlin.ble.client.mock.Proximity
import no.nordicsemi.kotlin.ble.core.CharacteristicProperty
import no.nordicsemi.kotlin.ble.core.LegacyAdvertisingSetParameters
import no.nordicsemi.kotlin.ble.core.Permission
import no.nordicsemi.kotlin.ble.core.and
import no.nordicsemi.kotlin.ble.environment.android.mock.MockAndroidEnvironment
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

private val SERVICE_UUID = Uuid.parse("4FAFC201-1FB5-459E-8FCC-C5C9C331914B")
private val OTA_CHARACTERISTIC_UUID = Uuid.parse("62ec0272-3ec5-11eb-b378-0242ac130005")
private val TX_CHARACTERISTIC_UUID = Uuid.parse("62ec0272-3ec5-11eb-b378-0242ac130003")

/**
 * Tests for BleOtaTransport service discovery via Nordic's Peripheral.profile() API. These validate the refactored
 * connect() path that replaced discoverCharacteristics().
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BleOtaTransportServiceDiscoveryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val address = "00:11:22:33:44:55"

    @Before
    fun setup() {
        Logger.setLogWriters(
            object : co.touchlab.kermit.LogWriter() {
                override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                    println("[$severity] $tag: $message")
                    throwable?.printStackTrace()
                }
            },
        )
    }

    @Test
    fun `connect fails when OTA service not found on device`() = runTest(testDispatcher) {
        val mockEnvironment = MockAndroidEnvironment.Api31(isBluetoothEnabled = true)
        val centralManager = CentralManager.mock(mockEnvironment, scope = backgroundScope)

        // Create a peripheral with a DIFFERENT service UUID (not the OTA service)
        val wrongServiceUuid = Uuid.parse("0000180A-0000-1000-8000-00805F9B34FB") // Device Info
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
                    Service(uuid = wrongServiceUuid) {
                        Characteristic(
                            uuid = OTA_CHARACTERISTIC_UUID,
                            properties =
                            CharacteristicProperty.WRITE and CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                            permission = Permission.WRITE,
                        )
                    }
                }
            }

        centralManager.simulatePeripherals(listOf(otaPeripheral))

        val scanner = org.meshtastic.core.ble.AndroidBleScanner(centralManager)
        val connectionFactory = org.meshtastic.core.ble.AndroidBleConnectionFactory(centralManager)
        val transport = BleOtaTransport(scanner, connectionFactory, address, testDispatcher)
        val result = transport.connect()

        assertTrue("Connect should fail when OTA service is missing", result.isFailure)
        transport.close()
    }

    @Test
    fun `connect fails when TX characteristic is missing`() = runTest(testDispatcher) {
        val mockEnvironment = MockAndroidEnvironment.Api31(isBluetoothEnabled = true)
        val centralManager = CentralManager.mock(mockEnvironment, scope = backgroundScope)

        // Create a peripheral with the OTA service but only the OTA characteristic (no TX)
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
                        // TX_CHARACTERISTIC intentionally omitted
                    }
                }
            }

        centralManager.simulatePeripherals(listOf(otaPeripheral))

        val scanner = org.meshtastic.core.ble.AndroidBleScanner(centralManager)
        val connectionFactory = org.meshtastic.core.ble.AndroidBleConnectionFactory(centralManager)
        val transport = BleOtaTransport(scanner, connectionFactory, address, testDispatcher)
        val result = transport.connect()

        assertTrue("Connect should fail when TX characteristic is missing", result.isFailure)
        transport.close()
    }

    @Test
    fun `connect fails when device is not found during scan`() = runTest(testDispatcher) {
        val mockEnvironment = MockAndroidEnvironment.Api31(isBluetoothEnabled = true)
        val centralManager = CentralManager.mock(mockEnvironment, scope = backgroundScope)

        // Don't simulate any peripherals — scan will find nothing
        val scanner = org.meshtastic.core.ble.AndroidBleScanner(centralManager)
        val connectionFactory = org.meshtastic.core.ble.AndroidBleConnectionFactory(centralManager)
        val transport = BleOtaTransport(scanner, connectionFactory, address, testDispatcher)
        val result = transport.connect()

        assertTrue("Connect should fail when device is not found", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(
            "Should be ConnectionFailed, got: $exception",
            exception is OtaProtocolException.ConnectionFailed,
        )
        transport.close()
    }

    @Test
    fun `connect succeeds with valid OTA service and characteristics`() = runTest(testDispatcher) {
        val mockEnvironment = MockAndroidEnvironment.Api31(isBluetoothEnabled = true)
        val centralManager = CentralManager.mock(mockEnvironment, scope = backgroundScope)

        val otaPeripheral =
            PeripheralSpec.simulatePeripheral(identifier = address, proximity = Proximity.IMMEDIATE) {
                advertising(
                    parameters = LegacyAdvertisingSetParameters(connectable = true, interval = 100.milliseconds),
                ) {
                    CompleteLocalName("ESP32-OTA")
                }
                connectable(
                    name = "ESP32-OTA",
                    eventHandler =
                    object : PeripheralSpecEventHandler {
                        override fun onConnectionRequest(
                            preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>,
                        ): ConnectionResult = ConnectionResult.Accept
                    },
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
        val result = transport.connect()

        assertTrue("Connect should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        transport.close()
    }
}

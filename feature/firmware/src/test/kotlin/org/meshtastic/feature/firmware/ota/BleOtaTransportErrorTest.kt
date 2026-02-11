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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class BleOtaTransportErrorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val address = "00:11:22:33:44:55"

    private val serviceUuid = UUID.fromString("4FAFC201-1FB5-459E-8FCC-C5C9C331914B")
    private val otaCharacteristicUuid = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130005")
    private val txCharacteristicUuid = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130003")

    private fun UUID.toKotlinUuid(): Uuid = Uuid.parse(this.toString())

    @Test
    fun `startOta fails when device rejects hash`() = runTest(testDispatcher) {
        val centralManager = CentralManager.Factory.mock(scope = backgroundScope)
        lateinit var otaPeripheral: PeripheralSpec<String>
        var txCharHandle: Int = -1

        val eventHandler =
            object : PeripheralSpecEventHandler {
                override fun onConnectionRequest(preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>) =
                    ConnectionResult.Accept

                override fun onWriteRequest(
                    characteristic: MockRemoteCharacteristic,
                    value: ByteArray,
                ): WriteResponse {
                    val command = value.decodeToString()
                    if (command.startsWith("OTA")) {
                        backgroundScope.launch {
                            delay(50.milliseconds)
                            otaPeripheral.simulateValueUpdate(txCharHandle, "ERR Hash Rejected\n".toByteArray())
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
                    Service(uuid = serviceUuid.toKotlinUuid()) {
                        Characteristic(
                            uuid = otaCharacteristicUuid.toKotlinUuid(),
                            properties =
                            CharacteristicProperty.WRITE and CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                            permission = Permission.WRITE,
                        )
                        txCharHandle =
                            Characteristic(
                                uuid = txCharacteristicUuid.toKotlinUuid(),
                                property = CharacteristicProperty.NOTIFY,
                                permission = Permission.READ,
                            )
                    }
                }
            }

        centralManager.simulatePeripherals(listOf(otaPeripheral))
        val transport = BleOtaTransport(centralManager, address, testDispatcher)

        transport.connect().getOrThrow()

        val result = transport.startOta(1024, "badhash") {}
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OtaProtocolException.HashRejected)

        transport.close()
    }

    @Test
    fun `streamFirmware fails when connection lost`() = runTest(testDispatcher) {
        val centralManager = CentralManager.Factory.mock(scope = backgroundScope)
        lateinit var otaPeripheral: PeripheralSpec<String>
        var txCharHandle: Int = -1

        val eventHandler =
            object : PeripheralSpecEventHandler {
                override fun onConnectionRequest(preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>) =
                    ConnectionResult.Accept

                override fun onWriteRequest(
                    characteristic: MockRemoteCharacteristic,
                    value: ByteArray,
                ): WriteResponse {
                    backgroundScope.launch {
                        delay(50.milliseconds)
                        otaPeripheral.simulateValueUpdate(txCharHandle, "OK\n".toByteArray())
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
                    Service(uuid = serviceUuid.toKotlinUuid()) {
                        Characteristic(
                            uuid = otaCharacteristicUuid.toKotlinUuid(),
                            properties =
                            CharacteristicProperty.WRITE and CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                            permission = Permission.WRITE,
                        )
                        txCharHandle =
                            Characteristic(
                                uuid = txCharacteristicUuid.toKotlinUuid(),
                                property = CharacteristicProperty.NOTIFY,
                                permission = Permission.READ,
                            )
                    }
                }
            }

        centralManager.simulatePeripherals(listOf(otaPeripheral))
        val transport = BleOtaTransport(centralManager, address, testDispatcher)

        transport.connect().getOrThrow()
        transport.startOta(1024, "hash") {}.getOrThrow()

        // Find the connected peripheral and disconnect it
        // We use isBonded=true to ensure it shows up in getBondedPeripherals()
        val peripheral = centralManager.getBondedPeripherals().first { it.address == address }
        peripheral.disconnect()

        // Wait for state propagation
        delay(100.milliseconds)

        val data = ByteArray(1024) { it.toByte() }
        val result = transport.streamFirmware(data, 512) {}

        assertTrue("Should fail due to connection loss", result.isFailure)
        assertTrue(result.exceptionOrNull() is OtaProtocolException.TransferFailed)
        assertTrue(result.exceptionOrNull()?.message?.contains("Connection lost") == true)

        transport.close()
    }

    @Test
    fun `streamFirmware fails on hash mismatch at verification`() = runTest(testDispatcher) {
        val centralManager = CentralManager.Factory.mock(scope = backgroundScope)
        lateinit var otaPeripheral: PeripheralSpec<String>
        var txCharHandle: Int = -1

        val eventHandler =
            object : PeripheralSpecEventHandler {
                override fun onConnectionRequest(preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>) =
                    ConnectionResult.Accept

                override fun onWriteRequest(
                    characteristic: MockRemoteCharacteristic,
                    value: ByteArray,
                ): WriteResponse {
                    backgroundScope.launch {
                        delay(50.milliseconds)
                        otaPeripheral.simulateValueUpdate(txCharHandle, "OK\n".toByteArray())
                    }
                    return WriteResponse.Success
                }

                override fun onWriteCommand(characteristic: MockRemoteCharacteristic, value: ByteArray) {
                    backgroundScope.launch {
                        delay(10.milliseconds)
                        otaPeripheral.simulateValueUpdate(txCharHandle, "ACK\n".toByteArray())
                    }
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
                    Service(uuid = serviceUuid.toKotlinUuid()) {
                        Characteristic(
                            uuid = otaCharacteristicUuid.toKotlinUuid(),
                            properties =
                            CharacteristicProperty.WRITE and CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                            permission = Permission.WRITE,
                        )
                        txCharHandle =
                            Characteristic(
                                uuid = txCharacteristicUuid.toKotlinUuid(),
                                property = CharacteristicProperty.NOTIFY,
                                permission = Permission.READ,
                            )
                    }
                }
            }

        centralManager.simulatePeripherals(listOf(otaPeripheral))
        val transport = BleOtaTransport(centralManager, address, testDispatcher)

        transport.connect().getOrThrow()
        transport.startOta(1024, "hash") {}.getOrThrow()

        // Setup final response to be a Hash Mismatch error after chunks are sent
        backgroundScope.launch {
            delay(1000.milliseconds)
            otaPeripheral.simulateValueUpdate(txCharHandle, "ERR Hash Mismatch\n".toByteArray())
        }

        val data = ByteArray(1024) { it.toByte() }
        val result = transport.streamFirmware(data, 512) {}

        assertTrue("Should fail due to hash mismatch, but got ${result.exceptionOrNull()}", result.isFailure)
        assertTrue(result.exceptionOrNull() is OtaProtocolException.VerificationFailed)

        transport.close()
    }
}

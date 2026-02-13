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
package com.geeksville.mesh.repository.radio

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.mock.mock
import no.nordicsemi.kotlin.ble.client.mock.ConnectionResult
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpec
import no.nordicsemi.kotlin.ble.client.mock.PeripheralSpecEventHandler
import no.nordicsemi.kotlin.ble.client.mock.Proximity
import no.nordicsemi.kotlin.ble.client.mock.ReadResponse
import no.nordicsemi.kotlin.ble.client.mock.internal.MockRemoteCharacteristic
import no.nordicsemi.kotlin.ble.core.CharacteristicProperty
import no.nordicsemi.kotlin.ble.core.LegacyAdvertisingSetParameters
import no.nordicsemi.kotlin.ble.core.Permission
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.ble.BleError
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMNUM_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.LOGRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.ble.MeshtasticBleConstants.TORADIO_CHARACTERISTIC
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class NordicBleInterfaceRetryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
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
    fun `write succeeds after one retry`() = runTest(testDispatcher) {
        val centralManager = CentralManager.Factory.mock(scope = backgroundScope)
        val service = mockk<RadioInterfaceService>(relaxed = true)

        var toRadioHandle: Int = -1
        var writeAttempts = 0
        var writtenValue: ByteArray? = null

        val eventHandler =
            object : PeripheralSpecEventHandler {
                override fun onConnectionRequest(
                    preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>,
                ): ConnectionResult = ConnectionResult.Accept

                override fun onWriteCommand(characteristic: MockRemoteCharacteristic, value: ByteArray) {
                    if (characteristic.instanceId == toRadioHandle) {
                        writeAttempts++
                        if (writeAttempts == 1) {
                            println("Simulating first write failure")
                            throw RuntimeException("Temporary failure")
                        }
                        println("Second write attempt succeeding")
                        writtenValue = value
                    }
                }

                override fun onReadRequest(characteristic: MockRemoteCharacteristic): ReadResponse =
                    ReadResponse.Success(byteArrayOf())
            }

        val peripheralSpec =
            PeripheralSpec.simulatePeripheral(identifier = address, proximity = Proximity.IMMEDIATE) {
                advertising(
                    parameters = LegacyAdvertisingSetParameters(connectable = true, interval = 100.milliseconds),
                ) {
                    CompleteLocalName("Meshtastic_Retry")
                }
                connectable(
                    name = "Meshtastic_Retry",
                    isBonded = true,
                    eventHandler = eventHandler,
                    cachedServices = {
                        Service(uuid = SERVICE_UUID) {
                            toRadioHandle =
                                Characteristic(
                                    uuid = TORADIO_CHARACTERISTIC,
                                    properties =
                                    setOf(
                                        CharacteristicProperty.WRITE,
                                        CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                                    ),
                                    permission = Permission.WRITE,
                                )
                            Characteristic(
                                uuid = FROMNUM_CHARACTERISTIC,
                                properties = setOf(CharacteristicProperty.NOTIFY),
                                permission = Permission.READ,
                            )
                            Characteristic(
                                uuid = FROMRADIO_CHARACTERISTIC,
                                properties = setOf(CharacteristicProperty.READ),
                                permission = Permission.READ,
                            )
                            Characteristic(
                                uuid = LOGRADIO_CHARACTERISTIC,
                                properties = setOf(CharacteristicProperty.NOTIFY),
                                permission = Permission.READ,
                            )
                        }
                    },
                )
            }

        centralManager.simulatePeripherals(listOf(peripheralSpec))
        delay(100.milliseconds)

        val nordicInterface =
            NordicBleInterface(
                serviceScope = this,
                centralManager = centralManager,
                service = service,
                address = address,
            )

        // Wait for connection and stable state
        delay(2000.milliseconds)
        verify(timeout = 5000) { service.onConnect() }

        // Clear initial discovery errors if any (sometimes mock emits empty list initially)
        clearMocks(service, answers = false, recordedCalls = true)

        // Test writing
        val dataToSend = byteArrayOf(0x01, 0x02, 0x03)
        nordicInterface.handleSendToRadio(dataToSend)

        // Give it time to process retries (500ms delay per retry in code)
        delay(1500.milliseconds)

        assert(writeAttempts == 2) { "Should have attempted write twice, but was $writeAttempts" }
        assert(writtenValue != null) { "Value should have been eventually written" }
        assert(writtenValue!!.contentEquals(dataToSend))

        // Verify we didn't disconnect due to the retryable error
        verify(exactly = 0) { service.onDisconnect(any<BleError.BluetoothError>()) }

        nordicInterface.close()
    }

    @Test
    fun `write fails after max retries`() = runTest(testDispatcher) {
        val uniqueAddress = "11:22:33:44:55:66"
        val centralManager = CentralManager.Factory.mock(scope = backgroundScope)
        val service = mockk<RadioInterfaceService>(relaxed = true)

        var toRadioHandle: Int = -1
        var writeAttempts = 0

        val eventHandler =
            object : PeripheralSpecEventHandler {
                override fun onConnectionRequest(
                    preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>,
                ): ConnectionResult = ConnectionResult.Accept

                override fun onWriteCommand(characteristic: MockRemoteCharacteristic, value: ByteArray) {
                    if (characteristic.instanceId == toRadioHandle) {
                        writeAttempts++
                        println("Simulating write failure #$writeAttempts")
                        throw RuntimeException("Persistent failure")
                    }
                }

                override fun onReadRequest(characteristic: MockRemoteCharacteristic): ReadResponse =
                    ReadResponse.Success(byteArrayOf())
            }

        val peripheralSpec =
            PeripheralSpec.simulatePeripheral(identifier = uniqueAddress, proximity = Proximity.IMMEDIATE) {
                advertising(
                    parameters = LegacyAdvertisingSetParameters(connectable = true, interval = 100.milliseconds),
                ) {
                    CompleteLocalName("Meshtastic_Fail")
                }
                connectable(
                    name = "Meshtastic_Fail",
                    isBonded = true,
                    eventHandler = eventHandler,
                    cachedServices = {
                        Service(uuid = SERVICE_UUID) {
                            toRadioHandle =
                                Characteristic(
                                    uuid = TORADIO_CHARACTERISTIC,
                                    properties =
                                    setOf(
                                        CharacteristicProperty.WRITE,
                                        CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                                    ),
                                    permission = Permission.WRITE,
                                )
                            Characteristic(
                                uuid = FROMNUM_CHARACTERISTIC,
                                properties = setOf(CharacteristicProperty.NOTIFY),
                                permission = Permission.READ,
                            )
                            Characteristic(
                                uuid = FROMRADIO_CHARACTERISTIC,
                                properties = setOf(CharacteristicProperty.READ),
                                permission = Permission.READ,
                            )
                            Characteristic(
                                uuid = LOGRADIO_CHARACTERISTIC,
                                properties = setOf(CharacteristicProperty.NOTIFY),
                                permission = Permission.READ,
                            )
                        }
                    },
                )
            }

        centralManager.simulatePeripherals(listOf(peripheralSpec))
        delay(100.milliseconds)

        val nordicInterface =
            NordicBleInterface(
                serviceScope = this,
                centralManager = centralManager,
                service = service,
                address = uniqueAddress,
            )

        // Wait for connection
        delay(2000.milliseconds)
        verify(timeout = 5000) { service.onConnect() }

        // Clear initial discovery errors
        clearMocks(service, answers = false, recordedCalls = true)

        // Trigger write which will fail repeatedly
        nordicInterface.handleSendToRadio(byteArrayOf(0x01))

        // Wait for all 3 attempts + delays (500ms * 2)
        delay(2500.milliseconds)

        assert(writeAttempts == 3) {
            "Should have attempted write 3 times (initial + 2 retries), but was $writeAttempts"
        }

        // Verify onDisconnect was called after retries exhausted
        // Nordic BLE wraps RuntimeException in BluetoothException
        verify { service.onDisconnect(any<BleError.BluetoothError>()) }

        nordicInterface.close()
    }
}

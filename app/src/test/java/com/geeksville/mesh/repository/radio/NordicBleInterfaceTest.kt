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
import no.nordicsemi.kotlin.ble.client.mock.WriteResponse
import no.nordicsemi.kotlin.ble.client.mock.internal.MockRemoteCharacteristic
import no.nordicsemi.kotlin.ble.core.CharacteristicProperty
import no.nordicsemi.kotlin.ble.core.LegacyAdvertisingSetParameters
import no.nordicsemi.kotlin.ble.core.Permission
import no.nordicsemi.kotlin.ble.core.and
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class NordicBleInterfaceTest {

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
    fun `full connection and notification flow`() = runTest(testDispatcher) {
        val centralManager = CentralManager.Factory.mock(scope = backgroundScope)
        val service = mockk<RadioInterfaceService>(relaxed = true)

        var fromNumHandle: Int = -1
        var logRadioHandle: Int = -1
        var fromRadioHandle: Int = -1
        var fromRadioValue: ByteArray = byteArrayOf()

        lateinit var otaPeripheral: PeripheralSpec<String>

        val eventHandler =
            object : PeripheralSpecEventHandler {
                override fun onConnectionRequest(
                    preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>,
                ): ConnectionResult = ConnectionResult.Accept

                override fun onWriteRequest(
                    characteristic: MockRemoteCharacteristic,
                    value: ByteArray,
                ): WriteResponse = WriteResponse.Success

                override fun onReadRequest(characteristic: MockRemoteCharacteristic): ReadResponse {
                    if (characteristic.instanceId == fromRadioHandle) {
                        return ReadResponse.Success(fromRadioValue)
                    }
                    return ReadResponse.Success(byteArrayOf())
                }
            }

        otaPeripheral =
            PeripheralSpec.simulatePeripheral(identifier = address, proximity = Proximity.IMMEDIATE) {
                advertising(
                    parameters = LegacyAdvertisingSetParameters(connectable = true, interval = 100.milliseconds),
                ) {
                    CompleteLocalName("Meshtastic_1234")
                }
                connectable(
                    name = "Meshtastic_1234",
                    isBonded = true,
                    eventHandler = eventHandler,
                    cachedServices = {
                        Service(uuid = BleConstants.BTM_SERVICE_UUID) {
                            Characteristic(
                                uuid = BleConstants.BTM_TORADIO_CHARACTER,
                                properties =
                                CharacteristicProperty.WRITE and CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                                permission = Permission.WRITE,
                            )
                            fromNumHandle =
                                Characteristic(
                                    uuid = BleConstants.BTM_FROMNUM_CHARACTER,
                                    properties = setOf(CharacteristicProperty.NOTIFY),
                                    permission = Permission.READ,
                                )
                            fromRadioHandle =
                                Characteristic(
                                    uuid = BleConstants.BTM_FROMRADIO_CHARACTER,
                                    properties = setOf(CharacteristicProperty.READ),
                                    permission = Permission.READ,
                                )
                            logRadioHandle =
                                Characteristic(
                                    uuid = BleConstants.BTM_LOGRADIO_CHARACTER,
                                    properties = setOf(CharacteristicProperty.NOTIFY),
                                    permission = Permission.READ,
                                )
                        }
                    },
                )
            }

        centralManager.simulatePeripherals(listOf(otaPeripheral))

        println("Bonded peripherals: ${centralManager.getBondedPeripherals().size}")
        centralManager.getBondedPeripherals().forEach { println("Found bonded peripheral: ${it.address}") }

        // Give it a moment to stabilize
        delay(100.milliseconds)

        // Create the interface
        println("Creating NordicBleInterface")
        val nordicInterface =
            NordicBleInterface(
                serviceScope = this,
                centralManager = centralManager,
                service = service,
                address = address,
            )

        // Wait for connection and discovery
        println("Waiting for connection...")
        delay(2000.milliseconds)

        println("Verifying onConnect...")
        verify(timeout = 5000) { service.onConnect() }
        println("onConnect verified.")

        // Set data available on fromRadio BEFORE notifying fromNum
        fromRadioValue = byteArrayOf(0xCA.toByte(), 0xFE.toByte())

        // Simulate a notification from fromNum (indicates there are packets to read)
        otaPeripheral.simulateValueUpdate(fromNumHandle, byteArrayOf(0x01))

        // Wait for drain to start
        delay(500.milliseconds)

        // Simulate a log radio notification
        val logData = "test log".toByteArray()
        otaPeripheral.simulateValueUpdate(logRadioHandle, logData)

        delay(500.milliseconds)

        // Explicitly stub handleFromRadio just in case relaxed mock fails
        io.mockk.every { service.handleFromRadio(any()) } returns Unit

        // Verify that handleFromRadio was called (any arguments) with timeout
        verify(timeout = 2000) { service.handleFromRadio(any()) }

        nordicInterface.close()
    }

    @Test
    fun `handleSendToRadio writes to toRadioCharacteristic`() = runTest(testDispatcher) {
        val centralManager = CentralManager.Factory.mock(scope = backgroundScope)
        val service = mockk<RadioInterfaceService>(relaxed = true)

        var toRadioHandle: Int = -1
        var writtenValue: ByteArray? = null

        val eventHandler =
            object : PeripheralSpecEventHandler {
                override fun onConnectionRequest(
                    preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>,
                ): ConnectionResult = ConnectionResult.Accept

                override fun onWriteRequest(
                    characteristic: MockRemoteCharacteristic,
                    value: ByteArray,
                ): WriteResponse {
                    // Keep this for WITH_RESPONSE
                    println("onWriteRequest: charId=${characteristic.instanceId}, toRadioHandle=$toRadioHandle")
                    if (characteristic.instanceId == toRadioHandle) {
                        writtenValue = value
                    }
                    return WriteResponse.Success
                }

                override fun onWriteCommand(characteristic: MockRemoteCharacteristic, value: ByteArray) {
                    // This is for WITHOUT_RESPONSE
                    println("onWriteCommand: charId=${characteristic.instanceId}, toRadioHandle=$toRadioHandle")
                    if (characteristic.instanceId == toRadioHandle) {
                        println("onWriteCommand matched! value=${value.toHexString()}")
                        writtenValue = value
                    } else {
                        println("onWriteCommand mismatch.")
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
                    CompleteLocalName("Meshtastic_1234")
                }
                connectable(
                    name = "Meshtastic_1234",
                    isBonded = true,
                    eventHandler = eventHandler,
                    cachedServices = {
                        Service(uuid = BleConstants.BTM_SERVICE_UUID) {
                            toRadioHandle =
                                Characteristic(
                                    uuid = BleConstants.BTM_TORADIO_CHARACTER,
                                    properties =
                                    setOf(
                                        CharacteristicProperty.WRITE,
                                        CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                                    ),
                                    permission = Permission.WRITE,
                                )
                                    .also {
                                        println("Captured toRadioHandle: $it")
                                        // toRadioHandle is assigned by the expression itself
                                    }
                            // Add other required chars to avoid discovery failure
                            Characteristic(
                                uuid = BleConstants.BTM_FROMNUM_CHARACTER,
                                properties = setOf(CharacteristicProperty.NOTIFY),
                                permission = Permission.READ,
                            )
                            Characteristic(
                                uuid = BleConstants.BTM_FROMRADIO_CHARACTER,
                                properties = setOf(CharacteristicProperty.READ),
                                permission = Permission.READ,
                            )
                            Characteristic(
                                uuid = BleConstants.BTM_LOGRADIO_CHARACTER,
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

        // Wait for connection
        delay(1000.milliseconds)
        verify(timeout = 2000) { service.onConnect() }

        // Test writing
        val dataToSend = byteArrayOf(0x01, 0x02, 0x03)
        nordicInterface.handleSendToRadio(dataToSend)

        // Give it time to process
        delay(500.milliseconds)

        assert(writtenValue != null) { "Value should have been written" }
        assert(writtenValue!!.contentEquals(dataToSend)) {
            "Written value ${writtenValue?.contentToString()} does not match expected ${dataToSend.contentToString()}"
        }

        nordicInterface.close()
    }

    @Test
    fun `disconnection triggers onDisconnect`() = runTest(testDispatcher) {
        val centralManager = CentralManager.Factory.mock(scope = backgroundScope)

        // Mock service
        val service = mockk<RadioInterfaceService>(relaxed = true)
        // Explicitly stub handleFromRadio just in case
        io.mockk.every { service.handleFromRadio(any()) } returns Unit

        val eventHandler =
            object : PeripheralSpecEventHandler {
                override fun onConnectionRequest(
                    preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>,
                ): ConnectionResult = ConnectionResult.Accept

                // Minimal implementation for connection test
                override fun onReadRequest(characteristic: MockRemoteCharacteristic): ReadResponse =
                    ReadResponse.Success(byteArrayOf())
            }

        val peripheralSpec =
            PeripheralSpec.simulatePeripheral(identifier = address, proximity = Proximity.IMMEDIATE) {
                advertising(
                    parameters = LegacyAdvertisingSetParameters(connectable = true, interval = 100.milliseconds),
                ) {
                    CompleteLocalName("Meshtastic_1234")
                }
                connectable(
                    name = "Meshtastic_1234",
                    isBonded = true,
                    eventHandler = eventHandler,
                    cachedServices = {
                        Service(uuid = BleConstants.BTM_SERVICE_UUID) {
                            Characteristic(
                                uuid = BleConstants.BTM_TORADIO_CHARACTER,
                                properties =
                                setOf(
                                    CharacteristicProperty.WRITE,
                                    CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                                ),
                                permission = Permission.WRITE,
                            )
                            Characteristic(
                                uuid = BleConstants.BTM_FROMNUM_CHARACTER,
                                properties = setOf(CharacteristicProperty.NOTIFY),
                                permission = Permission.READ,
                            )
                            Characteristic(
                                uuid = BleConstants.BTM_FROMRADIO_CHARACTER,
                                properties = setOf(CharacteristicProperty.READ),
                                permission = Permission.READ,
                            )
                            Characteristic(
                                uuid = BleConstants.BTM_LOGRADIO_CHARACTER,
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

        // Wait for connection
        delay(1000.milliseconds)
        verify(timeout = 2000) { service.onConnect() }

        // Find the connected peripheral from CentralManager to trigger disconnect
        val connectedPeripheral = centralManager.getBondedPeripherals().first { it.address == address }

        println("Simulating disconnect via peripheral.disconnect()")
        connectedPeripheral.disconnect()

        // Wait for disconnect event propagation
        delay(1000.milliseconds)

        // Verify onDisconnect was called on the service
        // NordicBleInterface calls onDisconnect(BleError.Disconnected)
        verify { service.onDisconnect(any<BleError.Disconnected>()) }

        nordicInterface.close()
    }

    @Test
    fun `discovery fails if required characteristic missing`() = runTest(testDispatcher) {
        val centralManager = CentralManager.Factory.mock(scope = backgroundScope)

        // Mock service
        val service = mockk<RadioInterfaceService>(relaxed = true)
        io.mockk.every { service.handleFromRadio(any()) } returns Unit

        val eventHandler =
            object : PeripheralSpecEventHandler {
                override fun onConnectionRequest(
                    preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>,
                ): ConnectionResult = ConnectionResult.Accept

                override fun onReadRequest(characteristic: MockRemoteCharacteristic): ReadResponse =
                    ReadResponse.Success(byteArrayOf())
            }

        val peripheralSpec =
            PeripheralSpec.simulatePeripheral(identifier = address, proximity = Proximity.IMMEDIATE) {
                advertising(
                    parameters = LegacyAdvertisingSetParameters(connectable = true, interval = 100.milliseconds),
                ) {
                    CompleteLocalName("Meshtastic_1234")
                }
                connectable(
                    name = "Meshtastic_1234",
                    isBonded = true,
                    eventHandler = eventHandler,
                    cachedServices = {
                        Service(uuid = BleConstants.BTM_SERVICE_UUID) {
                            // OMIT toRadio characteristic to force failure
                                /*
                                Characteristic(
                                    uuid = BleConstants.BTM_TORADIO_CHARACTER,
                                    properties = setOf(CharacteristicProperty.WRITE, CharacteristicProperty.WRITE_WITHOUT_RESPONSE),
                                    permission = Permission.WRITE
                                )
                                 */
                            Characteristic(
                                uuid = BleConstants.BTM_FROMNUM_CHARACTER,
                                properties = setOf(CharacteristicProperty.NOTIFY),
                                permission = Permission.READ,
                            )
                            Characteristic(
                                uuid = BleConstants.BTM_FROMRADIO_CHARACTER,
                                properties = setOf(CharacteristicProperty.READ),
                                permission = Permission.READ,
                            )
                            Characteristic(
                                uuid = BleConstants.BTM_LOGRADIO_CHARACTER,
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

        // Wait for connection and eventual failure
        delay(1000.milliseconds)

        // Verify that discovery failed
        verify { service.onDisconnect(any<BleError.DiscoveryFailed>()) }

        nordicInterface.close()
    }

    @Test
    fun `write exception triggers disconnect`() = runTest(testDispatcher) {
        val uniqueAddress = "11:22:33:44:55:66"
        val centralManager = CentralManager.Factory.mock(scope = backgroundScope)

        // Mock service
        val service = mockk<RadioInterfaceService>(relaxed = true)
        io.mockk.every { service.handleFromRadio(any()) } returns Unit

        val eventHandler =
            object : PeripheralSpecEventHandler {
                override fun onConnectionRequest(
                    preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>,
                ): ConnectionResult = ConnectionResult.Accept

                override fun onReadRequest(characteristic: MockRemoteCharacteristic): ReadResponse =
                    ReadResponse.Success(byteArrayOf())

                // Throw exception on write
                override fun onWriteCommand(characteristic: MockRemoteCharacteristic, value: ByteArray): Unit =
                    throw RuntimeException("Simulated write failure")
            }

        val peripheralSpec =
            PeripheralSpec.simulatePeripheral(identifier = uniqueAddress, proximity = Proximity.IMMEDIATE) {
                advertising(
                    parameters = LegacyAdvertisingSetParameters(connectable = true, interval = 100.milliseconds),
                ) {
                    CompleteLocalName("Meshtastic_1234")
                }
                connectable(
                    name = "Meshtastic_1234",
                    isBonded = true,
                    eventHandler = eventHandler,
                    cachedServices = {
                        Service(uuid = BleConstants.BTM_SERVICE_UUID) {
                            Characteristic(
                                uuid = BleConstants.BTM_TORADIO_CHARACTER,
                                properties =
                                setOf(
                                    CharacteristicProperty.WRITE,
                                    CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                                ),
                                permission = Permission.WRITE,
                            )
                            Characteristic(
                                uuid = BleConstants.BTM_FROMNUM_CHARACTER,
                                properties = setOf(CharacteristicProperty.NOTIFY),
                                permission = Permission.READ,
                            )
                            Characteristic(
                                uuid = BleConstants.BTM_FROMRADIO_CHARACTER,
                                properties = setOf(CharacteristicProperty.READ),
                                permission = Permission.READ,
                            )
                            Characteristic(
                                uuid = BleConstants.BTM_LOGRADIO_CHARACTER,
                                properties = setOf(CharacteristicProperty.NOTIFY),
                                permission = Permission.READ,
                            )
                        }
                    },
                )
            }

        centralManager.simulatePeripherals(listOf(peripheralSpec))
        delay(1000.milliseconds)

        val nordicInterface =
            NordicBleInterface(
                serviceScope = this,
                centralManager = centralManager,
                service = service,
                address = uniqueAddress,
            )

        // Wait for connection
        delay(1000.milliseconds)
        verify(timeout = 2000) { service.onConnect() }

        // Trigger write which will fail
        nordicInterface.handleSendToRadio(byteArrayOf(0x01))

        // Wait for error propagation
        delay(500.milliseconds)

        // Verify onDisconnect was called with error
        verify { service.onDisconnect(any<BleError>()) }

        nordicInterface.close()
    }
}

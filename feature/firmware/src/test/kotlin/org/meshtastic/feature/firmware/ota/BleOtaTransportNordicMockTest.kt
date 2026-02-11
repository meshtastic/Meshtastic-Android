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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val SERVICE_UUID = UUID.fromString("4FAFC201-1FB5-459E-8FCC-C5C9C331914B")
private val OTA_CHARACTERISTIC_UUID = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130005")
private val TX_CHARACTERISTIC_UUID = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130003")

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class BleOtaTransportNordicMockTest {

    private val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()
    private val address = "00:11:22:33:44:55"

    private fun java.util.UUID.toKotlinUuid(): Uuid = Uuid.parse(this.toString())

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
    fun `full ota flow with nordic mocks`() = runTest(testDispatcher) {
        // Use default mock environment
        // Use backgroundScope so that simulation coroutines are cancelled after the test
        val centralManager = CentralManager.Factory.mock(scope = backgroundScope)

        var otaCharHandle: Int = -1
        var txCharHandle: Int = -1

        // Use a property to hold the peripheral since we need it in the event handler
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
                    val command = value.decodeToString()
                    if (command.startsWith("OTA")) {
                        backgroundScope.launch {
                            delay(50.milliseconds)
                            otaPeripheral.simulateValueUpdate(txCharHandle, "OK\n".toByteArray())
                        }
                    }
                    return WriteResponse.Success
                }

                override fun onWriteCommand(characteristic: MockRemoteCharacteristic, value: ByteArray) {
                    // For firmware chunks (WITHOUT_RESPONSE)
                    backgroundScope.launch {
                        delay(10.milliseconds)
                        // In the real protocol, ACK is sent after each chunk.
                        // OK is sent after the final chunk is verified.
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
                connectable(name = "ESP32-OTA", eventHandler = eventHandler) {
                    Service(uuid = SERVICE_UUID.toKotlinUuid()) {
                        otaCharHandle =
                            Characteristic(
                                uuid = OTA_CHARACTERISTIC_UUID.toKotlinUuid(),
                                properties =
                                CharacteristicProperty.WRITE and CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                                permission = Permission.WRITE,
                            )
                        txCharHandle =
                            Characteristic(
                                uuid = TX_CHARACTERISTIC_UUID.toKotlinUuid(),
                                property = CharacteristicProperty.NOTIFY,
                                permission = Permission.READ,
                            )
                    }
                }
            }

        centralManager.simulatePeripherals(listOf(otaPeripheral))

        val transport = BleOtaTransport(centralManager, address, testDispatcher)

        // 1. Connect
        // Note: BleOtaTransport includes a 5s delay at the start of connect()
        val connectResult = transport.connect()
        assertTrue("Connection failed: ${connectResult.exceptionOrNull()}", connectResult.isSuccess)

        // 2. Start OTA
        val startResult = transport.startOta(1024L, "somehash") {}
        assertTrue("Start OTA failed: ${startResult.exceptionOrNull()}", startResult.isSuccess)

        // 3. Stream firmware
        // Need to simulate an OK at the very end
        backgroundScope.launch {
            // Wait for chunks to be sent. 1024 bytes with 512 chunk size = 2 chunks.
            // Each chunk takes 10ms + ACK processing.
            delay(500.milliseconds)
            otaPeripheral.simulateValueUpdate(txCharHandle, "OK\n".toByteArray())
        }

        val data = ByteArray(1024) { it.toByte() }
        val streamResult = transport.streamFirmware(data, 512) {}
        assertTrue("Stream firmware failed: ${streamResult.exceptionOrNull()}", streamResult.isSuccess)

        transport.close()
    }
}

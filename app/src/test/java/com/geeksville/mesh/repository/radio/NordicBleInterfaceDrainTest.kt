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

import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.junit.Test
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMNUM_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.LOGRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.ble.MeshtasticBleConstants.TORADIO_CHARACTERISTIC
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class NordicBleInterfaceDrainTest {

    private val testDispatcher = StandardTestDispatcher()
    private val address = "00:11:22:33:44:55"

    @Test
    fun `drainPacketQueueAndDispatch reads multiple packets until empty`() = runTest(testDispatcher) {
        val centralManager = CentralManager.Factory.mock(scope = backgroundScope)
        val service = mockk<RadioInterfaceService>(relaxed = true)

        var fromRadioHandle: Int = -1
        var fromNumHandle: Int = -1
        val packetsToRead = mutableListOf(byteArrayOf(0x01), byteArrayOf(0x02), byteArrayOf(0x03))

        val eventHandler =
            object : PeripheralSpecEventHandler {
                override fun onConnectionRequest(preferredPhy: List<no.nordicsemi.kotlin.ble.core.Phy>) =
                    ConnectionResult.Accept

                override fun onReadRequest(characteristic: MockRemoteCharacteristic): ReadResponse {
                    if (characteristic.instanceId == fromRadioHandle) {
                        return if (packetsToRead.isNotEmpty()) {
                            val p = packetsToRead.removeAt(0)
                            println("Mock: Returning packet ${p.contentToString()}")
                            ReadResponse.Success(p)
                        } else {
                            println("Mock: Queue empty, returning empty")
                            ReadResponse.Success(byteArrayOf())
                        }
                    }
                    return ReadResponse.Success(byteArrayOf())
                }

                override fun onWriteRequest(characteristic: MockRemoteCharacteristic, value: ByteArray) =
                    WriteResponse.Success
            }

        val peripheralSpec =
            PeripheralSpec.simulatePeripheral(identifier = address, proximity = Proximity.IMMEDIATE) {
                advertising(
                    parameters = LegacyAdvertisingSetParameters(connectable = true, interval = 100.milliseconds),
                ) {
                    CompleteLocalName("Meshtastic_Drain")
                }
                connectable(
                    name = "Meshtastic_Drain",
                    isBonded = true,
                    eventHandler = eventHandler,
                    cachedServices = {
                        Service(uuid = SERVICE_UUID) {
                            Characteristic(
                                uuid = TORADIO_CHARACTERISTIC,
                                properties =
                                setOf(
                                    CharacteristicProperty.WRITE,
                                    CharacteristicProperty.WRITE_WITHOUT_RESPONSE,
                                ),
                                permission = Permission.WRITE,
                            )
                            fromNumHandle =
                                Characteristic(
                                    uuid = FROMNUM_CHARACTERISTIC,
                                    properties = setOf(CharacteristicProperty.NOTIFY),
                                    permission = Permission.READ,
                                )
                            fromRadioHandle =
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

        val nordicInterface =
            NordicBleInterface(
                serviceScope = this,
                centralManager = centralManager,
                service = service,
                address = address,
            )

        // Wait for connection
        delay(2000.milliseconds)
        verify(timeout = 5000) { service.onConnect() }
        clearMocks(service, answers = false, recordedCalls = true)

        // Trigger drain
        println("Simulating FromNum notification...")
        peripheralSpec.simulateValueUpdate(fromNumHandle, byteArrayOf(0x01))

        // Wait for all packets to be processed
        delay(2000.milliseconds)

        // Verify handleFromRadio was called 3 times
        verify(timeout = 2000) { service.handleFromRadio(p = byteArrayOf(0x01)) }
        verify(timeout = 2000) { service.handleFromRadio(p = byteArrayOf(0x02)) }
        verify(timeout = 2000) { service.handleFromRadio(p = byteArrayOf(0x03)) }

        assert(packetsToRead.isEmpty()) { "All packets should have been read" }

        nordicInterface.close()
    }
}

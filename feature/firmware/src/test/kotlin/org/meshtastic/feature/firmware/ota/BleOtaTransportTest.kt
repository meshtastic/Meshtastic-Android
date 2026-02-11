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

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.RemoteCharacteristic
import no.nordicsemi.kotlin.ble.client.RemoteService
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.client.android.ScanResult
import no.nordicsemi.kotlin.ble.core.ConnectionState
import org.junit.Test
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

private val SERVICE_UUID = UUID.fromString("4FAFC201-1FB5-459E-8FCC-C5C9C331914B")
private val OTA_CHARACTERISTIC_UUID = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130005")
private val TX_CHARACTERISTIC_UUID = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130003")

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class BleOtaTransportTest {

    private val centralManager: CentralManager = mockk()
    private val address = "00:11:22:33:44:55"
    private val testDispatcher = UnconfinedTestDispatcher()
    private val transport = BleOtaTransport(centralManager, address, testDispatcher)

    @Test
    fun `race condition check - response before waitForResponse`() = runTest(testDispatcher) {
        val peripheral: Peripheral = mockk(relaxed = true)
        val otaChar: RemoteCharacteristic = mockk(relaxed = true)
        val txChar: RemoteCharacteristic = mockk(relaxed = true)
        val service: RemoteService = mockk(relaxed = true)
        val scanResult: ScanResult = mockk()

        every { scanResult.peripheral } returns peripheral

        // Mock the scan call. It takes a Duration and a lambda.
        every { centralManager.scan(any(), any()) } returns flowOf(scanResult)

        every { peripheral.address } returns address
        every { peripheral.state } returns MutableStateFlow(ConnectionState.Connected)

        coEvery { peripheral.services(any()) } returns MutableStateFlow(listOf(service))
        every { service.uuid } returns SERVICE_UUID.toKotlinUuid()
        every { service.characteristics } returns listOf(otaChar, txChar)
        every { otaChar.uuid } returns OTA_CHARACTERISTIC_UUID.toKotlinUuid()
        every { txChar.uuid } returns TX_CHARACTERISTIC_UUID.toKotlinUuid()

        coEvery { centralManager.connect(any(), any()) } returns Unit

        val notificationFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1)
        every { txChar.subscribe() } returns notificationFlow

        // Connect
        transport.connect().getOrThrow()

        // Simulate sending a command and getting a response BEFORE calling startOta
        // This is tricky to simulate exactly as in the real race, but we can verify
        // if responseFlow is indeed dropping messages.

        // In startOta:
        // 1. sendCommand(command)
        // 2. waitForResponse() -> responseFlow.first()

        // If the device is super fast, the notification arrives between 1 and 2.

        val size = 100L
        val hash = "hash"

        // We mock write to immediately emit to notificationFlow
        coEvery { otaChar.write(any(), any()) } coAnswers { notificationFlow.emit("OK\n".toByteArray()) }

        val result = transport.startOta(size, hash) {}
        assert(result.isSuccess)
    }
}

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

import io.mockk.coEvery
import io.mockk.coVerify
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class BleOtaTransportMtuTest {

    private val centralManager: CentralManager = mockk(relaxed = true)
    private val address = "00:11:22:33:44:55"
    private val testDispatcher = UnconfinedTestDispatcher()
    private val transport = BleOtaTransport(centralManager, address, testDispatcher)

    @Test
    fun `connect requests MTU`() = runTest(testDispatcher) {
        val peripheral: Peripheral = mockk(relaxed = true)
        val otaChar: RemoteCharacteristic = mockk(relaxed = true)
        val txChar: RemoteCharacteristic = mockk(relaxed = true)
        val service: RemoteService = mockk(relaxed = true)
        val scanResult: ScanResult = mockk(relaxed = true)

        val serviceUuid = Uuid.parse("4FAFC201-1FB5-459E-8FCC-C5C9C331914B")
        val otaCharUuid = Uuid.parse("62ec0272-3ec5-11eb-b378-0242ac130005")
        val txCharUuid = Uuid.parse("62ec0272-3ec5-11eb-b378-0242ac130003")

        every { scanResult.peripheral } returns peripheral
        every { centralManager.scan(any(), any()) } returns flowOf(scanResult)
        every { peripheral.address } returns address
        every { peripheral.state } returns MutableStateFlow(ConnectionState.Connected)
        every { peripheral.isConnected } returns true
        coEvery { peripheral.services(any()) } returns MutableStateFlow(listOf(service))
        every { service.uuid } returns serviceUuid
        every { service.characteristics } returns listOf(otaChar, txChar)
        every { otaChar.uuid } returns otaCharUuid
        every { txChar.uuid } returns txCharUuid
        coEvery { centralManager.connect(any(), any()) } returns Unit
        every { txChar.subscribe() } returns MutableSharedFlow()

        transport.connect().getOrThrow()

        // Verify connect was called with automaticallyRequestHighestValueLength = true
        coVerify {
            centralManager.connect(
                peripheral,
                CentralManager.ConnectionOptions.AutoConnect(automaticallyRequestHighestValueLength = true),
            )
        }
    }
}

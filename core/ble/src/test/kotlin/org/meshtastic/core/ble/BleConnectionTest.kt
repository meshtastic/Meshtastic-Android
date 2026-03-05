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
package org.meshtastic.core.ble

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.RemoteServices
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import org.junit.Test
import kotlin.uuid.Uuid

class BleConnectionTest {
    @Test(expected = Exception::class)
    fun `discoverCharacteristics throws exception when service discovery fails`() = runTest {
        val centralManager = mockk<no.nordicsemi.kotlin.ble.client.android.CentralManager>(relaxed = true)
        val peripheral = mockk<Peripheral>(relaxed = true)
        val bleConnection = BleConnection(centralManager, this)

        // Mock peripheral property (internal access)
        val peripheralField = BleConnection::class.java.getDeclaredField("peripheral")
        peripheralField.isAccessible = true
        peripheralField.set(bleConnection, peripheral)

        val serviceUuid = Uuid.random()
        val servicesFlow =
            MutableStateFlow<RemoteServices>(RemoteServices.Failed(RemoteServices.Failed.Reason.EmptyResult))

        every { peripheral.services(any()) } returns servicesFlow

        bleConnection.discoverCharacteristics(serviceUuid, listOf(Uuid.random()))
    }
}

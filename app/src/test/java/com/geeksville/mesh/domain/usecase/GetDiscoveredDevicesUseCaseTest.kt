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
package com.geeksville.mesh.domain.usecase

import android.hardware.usb.UsbManager
import com.geeksville.mesh.repository.network.NetworkRepository
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.repository.usb.UsbRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.ble.BluetoothState
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.datastore.RecentAddressesDataSource

class GetDiscoveredDevicesUseCaseTest {

    private lateinit var bluetoothRepository: BluetoothRepository
    private lateinit var networkRepository: NetworkRepository
    private lateinit var recentAddressesDataSource: RecentAddressesDataSource
    private lateinit var nodeRepository: NodeRepository
    private lateinit var databaseManager: DatabaseManager
    private lateinit var usbRepository: UsbRepository
    private lateinit var radioInterfaceService: RadioInterfaceService
    private lateinit var usbManagerLazy: dagger.Lazy<UsbManager>
    private lateinit var useCase: GetDiscoveredDevicesUseCase

    @Before
    fun setUp() {
        bluetoothRepository = mockk()
        networkRepository = mockk()
        recentAddressesDataSource = mockk(relaxed = true)
        nodeRepository = mockk()
        databaseManager = mockk(relaxed = true)
        usbRepository = mockk()
        radioInterfaceService = mockk()
        usbManagerLazy = mockk()

        useCase =
            GetDiscoveredDevicesUseCase(
                bluetoothRepository = bluetoothRepository,
                networkRepository = networkRepository,
                recentAddressesDataSource = recentAddressesDataSource,
                nodeRepository = nodeRepository,
                databaseManager = databaseManager,
                usbRepository = usbRepository,
                radioInterfaceService = radioInterfaceService,
                usbManagerLazy = usbManagerLazy,
            )

        // Default empty flows
        every { bluetoothRepository.state } returns MutableStateFlow(BluetoothState())
        every { networkRepository.resolvedList } returns MutableStateFlow(emptyList())
        every { recentAddressesDataSource.recentAddresses } returns MutableStateFlow(emptyList())
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { usbRepository.serialDevices } returns MutableStateFlow(emptyMap())
    }

    @Test
    fun `invoke returns discovered devices with mock if enabled`() = runTest {
        // Act
        val result = useCase.invoke(showMock = true).first()

        // Assert
        assertEquals(1, result.usbDevices.size) // The Demo Mode mock device
        assertEquals("Demo Mode", result.usbDevices.first().name)
    }

    @Test
    fun `invoke returns empty lists when no devices are discovered`() = runTest {
        // Act
        val result = useCase.invoke(showMock = false).first()

        // Assert
        assertEquals(0, result.bleDevices.size)
        assertEquals(0, result.usbDevices.size)
        assertEquals(0, result.discoveredTcpDevices.size)
        assertEquals(0, result.recentTcpDevices.size)
    }
}

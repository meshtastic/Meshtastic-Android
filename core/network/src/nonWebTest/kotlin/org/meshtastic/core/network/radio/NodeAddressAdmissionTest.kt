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
package org.meshtastic.core.network.radio

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.testing.FakeBluetoothRepository
import kotlin.test.Test
import kotlin.test.assertTrue

/** The in-process node is admitted on any hardware: it needs no radio, no permission and no unlock gesture. */
class NodeAddressAdmissionTest {

    private val bluetoothRepository = FakeBluetoothRepository()

    private fun factory() = object :
        BaseRadioTransportFactory(
            scanner = mock<BleScanner>(MockMode.autofill),
            bluetoothRepository = bluetoothRepository,
            connectionFactory = mock<BleConnectionFactory>(MockMode.autofill),
            dispatchers =
            CoroutineDispatchers(
                io = Dispatchers.Unconfined,
                main = Dispatchers.Unconfined,
                default = Dispatchers.Unconfined,
            ),
        ) {
        override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.NODE)

        override val mockTransportEnabled: StateFlow<Boolean> = MutableStateFlow(false)

        override val isReplayTransportAvailable: Boolean = false

        override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport =
            NopRadioTransport(address)
    }

    @Test
    fun `the node address is admitted without Bluetooth LE`() {
        bluetoothRepository.isSupported = false

        assertTrue(factory().isAddressValid(InterfaceId.NODE.id.toString()))
    }

    @Test
    fun `the node address is admitted while virtual transports are locked`() {
        assertTrue(factory().isAddressValid(InterfaceId.NODE.id.toString()))
    }
}

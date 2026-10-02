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
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.testing.FakeBluetoothRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A saved BLE address restored onto hardware with no Bluetooth LE must be refused, so the radio service falls into its
 * "No valid address" no-op instead of arming a BLE transport that retries forever.
 */
class BleAddressAdmissionTest {

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
        override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.TCP)

        override val mockTransportEnabled: StateFlow<Boolean> = MutableStateFlow(false)

        override val isReplayTransportAvailable: Boolean = false

        override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport =
            NopRadioTransport(address)
    }

    @Test
    fun `BLE addresses are refused on hardware without Bluetooth LE`() {
        bluetoothRepository.isSupported = false
        val factory = factory()

        assertFalse(factory.isAddressValid("x11:22:33:44:55:66"))
        assertFalse(factory.isAddressValid("!11:22:33:44:55:66"))
    }

    @Test
    fun `other transports stay valid on hardware without Bluetooth LE`() {
        bluetoothRepository.isSupported = false
        val factory = factory()

        assertTrue(factory.isAddressValid("t10.0.0.2"))
        assertTrue(factory.isAddressValid("s/dev/ttyUSB0"))
    }

    @Test
    fun `BLE addresses are admitted when Bluetooth LE is present`() {
        val factory = factory()

        assertTrue(factory.isAddressValid("x11:22:33:44:55:66"))
        assertTrue(factory.isAddressValid("!11:22:33:44:55:66"))
    }
}

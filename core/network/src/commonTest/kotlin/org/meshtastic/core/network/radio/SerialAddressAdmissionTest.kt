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
 * A saved serial address restored onto hardware with no USB host must be refused, so the radio service falls into its
 * "No valid address" no-op instead of building a serial transport that can never find its device.
 */
class SerialAddressAdmissionTest {

    private fun factory(serialSupported: Boolean) = object :
        BaseRadioTransportFactory(
            scanner = mock<BleScanner>(MockMode.autofill),
            bluetoothRepository = FakeBluetoothRepository(),
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

        override val isSerialSupported: Boolean = serialSupported

        override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport =
            NopRadioTransport(address)
    }

    @Test
    fun `serial addresses are refused on hardware without USB host`() {
        assertFalse(factory(serialSupported = false).isAddressValid("s1027:29987:0"))
    }

    @Test
    fun `other transports stay valid on hardware without USB host`() {
        val factory = factory(serialSupported = false)

        assertTrue(factory.isAddressValid("t10.0.0.2"))
        assertTrue(factory.isAddressValid("x11:22:33:44:55:66"))
    }

    @Test
    fun `serial addresses are admitted with USB host whether or not the device is present`() {
        assertTrue(factory(serialSupported = true).isAddressValid("s1027:29987:0"))
    }
}

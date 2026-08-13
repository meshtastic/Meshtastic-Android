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
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Demo Mode gate has to govern *both* halves of the feature.
 *
 * Showing the entry in the Connections list is one path; admitting its `m`/`r` address is another. When those two read
 * different sources the entry appears and then silently refuses to connect — which is why they now share one flow.
 */
class MockTransportAddressAdmissionTest {

    private val gate = MutableStateFlow(false)

    private val factory =
        object :
            BaseRadioTransportFactory(
                scanner = mock<BleScanner>(MockMode.autofill),
                bluetoothRepository = mock<BluetoothRepository>(MockMode.autofill),
                connectionFactory = mock<BleConnectionFactory>(MockMode.autofill),
                dispatchers =
                CoroutineDispatchers(
                    io = Dispatchers.Unconfined,
                    main = Dispatchers.Unconfined,
                    default = Dispatchers.Unconfined,
                ),
            ) {
            override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.BLE)

            override val mockTransportEnabled: StateFlow<Boolean> = gate

            // Deliberately false: admission of the `r` address is governed by the gate alone. Whether the capture
            // asset ships only decides if the entry is worth offering, and must not make `r` connectable when Demo
            // Mode is locked.
            override val isReplayTransportAvailable: Boolean = false

            override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport =
                NopRadioTransport(address)
        }

    @Test
    fun `virtual transport addresses are refused while the gate is closed`() {
        gate.value = false

        assertFalse(factory.isAddressValid("m"), "mock address must not be admitted when Demo Mode is locked")
        assertFalse(factory.isAddressValid("r"), "replay address must not be admitted when Demo Mode is locked")
    }

    @Test
    fun `virtual transport addresses are admitted once the gate opens`() {
        gate.value = true

        assertTrue(factory.isAddressValid("m"))
        assertTrue(factory.isAddressValid("r"))
    }

    @Test
    fun `the gate is re-read on every check rather than captured once`() {
        assertFalse(factory.isAddressValid("m"))

        gate.value = true

        assertTrue(factory.isAddressValid("m"), "admission must track a mid-session unlock")
    }

    @Test
    fun `real transports are unaffected by the gate`() {
        gate.value = false

        assertTrue(factory.isAddressValid("t10.0.0.2"), "TCP must stay valid")
        assertTrue(factory.isAddressValid("x11:22:33:44:55:66"), "BLE must stay valid")
        assertFalse(factory.isAddressValid(null))
        assertFalse(factory.isAddressValid(""))
    }
}

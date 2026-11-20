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
 * A TCP address saved where TCP works, then restored in a browser, must be refused so the radio service's reconnect
 * paths fall into their "No valid address" no-op instead of asking the factory for a transport it cannot build.
 */
class TcpAddressAdmissionTest {

    private fun factory(vararg types: DeviceType, rawTcp: Boolean = DeviceType.TCP in types) = object :
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
        override val supportedDeviceTypes: List<DeviceType> = types.toList()

        override val isRawTcpSupported: Boolean = rawTcp

        override val mockTransportEnabled: StateFlow<Boolean> = MutableStateFlow(false)

        override val isReplayTransportAvailable: Boolean = false

        override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport =
            error("no transport is built for an address that is refused")
    }

    @Test
    fun `tcp addresses are refused where the platform has no tcp transport`() {
        assertFalse(factory(DeviceType.BLE, DeviceType.USB).isAddressValid("t10.0.0.2"))
    }

    @Test
    fun `tcp addresses are admitted where the platform supports tcp`() {
        assertTrue(factory(DeviceType.BLE, DeviceType.TCP, DeviceType.USB).isAddressValid("t10.0.0.2"))
    }

    @Test
    fun `a browser network pane admits a websocket address but not a raw tcp one`() {
        val browser = factory(DeviceType.BLE, DeviceType.TCP, DeviceType.USB, rawTcp = false)
        assertTrue(browser.isAddressValid("wws://10.0.0.2/api/v1/ws"))
        assertFalse(browser.isAddressValid("t10.0.0.2"))
    }
}

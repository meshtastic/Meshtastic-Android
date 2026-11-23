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
package org.meshtastic.core.data.radio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.repository.RadioLink
import org.meshtastic.core.testing.FakeBluetoothRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which saved addresses a platform admits and which link each one opens. A refused address leaves the radio service in
 * its "No valid address" no-op instead of building a client that can never connect.
 */
class RadioLinksAdmissionTest {

    private val bluetoothRepository = FakeBluetoothRepository()
    private val gate = MutableStateFlow(false)

    private inner class Links(
        vararg types: DeviceType = arrayOf(DeviceType.BLE, DeviceType.TCP),
        private val rawTcp: Boolean = DeviceType.TCP in types,
        private val serialSupported: Boolean = true,
    ) : BaseRadioLinks(
        bluetoothRepository,
        RadioSessionPipeline(unused(), unused(), unused()),
    ) {
        override val supportedDeviceTypes: List<DeviceType> = types.toList()

        override val isRawTcpSupported: Boolean
            get() = rawTcp

        override val isSerialSupported: Boolean
            get() = serialSupported

        override val mockTransportEnabled: StateFlow<Boolean> = gate

        // Admission of `r` is governed by the gate alone; whether the capture ships only decides the picker entry.
        override val isReplayTransportAvailable: Boolean = false

        val opened = mutableListOf<String>()

        override fun openBle(address: String, scope: CoroutineScope): RadioLink? =
            null.also { opened += "ble:$address" }

        override fun openTcp(rest: String, scope: CoroutineScope): RadioLink? = null.also { opened += "tcp:$rest" }

        override fun openWebSocket(url: String, scope: CoroutineScope): RadioLink? = null.also { opened += "ws:$url" }

        override fun openSerial(rest: String, scope: CoroutineScope): RadioLink? =
            null.also { opened += "serial:$rest" }
    }

    @Test
    fun `BLE addresses are refused on hardware without Bluetooth LE`() {
        bluetoothRepository.isSupported = false
        val links = Links()

        assertFalse(links.isAddressValid("x11:22:33:44:55:66"))
        assertFalse(links.isAddressValid("!11:22:33:44:55:66"))
        assertTrue(links.isAddressValid("t10.0.0.2"), "other transports stay valid without Bluetooth LE")
    }

    @Test
    fun `BLE addresses are admitted when Bluetooth LE is present`() {
        val links = Links()

        assertTrue(links.isAddressValid("x11:22:33:44:55:66"))
        assertTrue(links.isAddressValid("!11:22:33:44:55:66"))
    }

    @Test
    fun `serial addresses are refused on hardware without USB host`() {
        assertFalse(Links(serialSupported = false).isAddressValid("s1027:29987:0"))
        assertTrue(Links(serialSupported = true).isAddressValid("s1027:29987:0"))
    }

    @Test
    fun `a browser network pane admits a websocket address but not a raw tcp one`() {
        val browser = Links(DeviceType.BLE, DeviceType.TCP, DeviceType.USB, rawTcp = false)

        assertTrue(browser.isAddressValid("wws://10.0.0.2/api/v1/ws"))
        assertFalse(browser.isAddressValid("t10.0.0.2"))
        assertFalse(Links(DeviceType.BLE, DeviceType.USB).isAddressValid("t10.0.0.2"))
    }

    @Test
    fun `virtual radios are admitted only while the Demo Mode gate is open as read on every check`() {
        val links = Links()
        assertFalse(links.isAddressValid("m"), "mock address must not be admitted when Demo Mode is locked")
        assertFalse(links.isAddressValid("r"), "replay address must not be admitted when Demo Mode is locked")

        gate.value = true

        assertTrue(links.isAddressValid("m"), "admission must track a mid-session unlock")
        assertTrue(links.isAddressValid("r"))
        assertFalse(links.isAddressValid(null))
        assertFalse(links.isAddressValid(""))
    }

    @Test
    fun `each prefix reaches its link with the prefix stripped`() {
        val links = Links()
        val scope = TestScope()

        links.open("x11:22:33:44:55:66", scope)
        links.open("!11:22:33:44:55:66", scope)
        links.open("t10.0.0.2:4403", scope)
        links.open("s/dev/ttyACM0", scope)
        // A saved bare WebSocket address gets firmware's path.
        links.open("wws://127.0.0.1:4404", scope)

        assertEquals(
            listOf(
                "ble:11:22:33:44:55:66",
                "ble:11:22:33:44:55:66",
                "tcp:10.0.0.2:4403",
                "serial:/dev/ttyACM0",
                "ws:ws://127.0.0.1:4404/api/v1/ws",
            ),
            links.opened,
        )
    }

    @Test
    fun `a no-device or unknown address opens nothing`() {
        val links = Links()

        assertNull(links.open("nanything", TestScope()))
        assertNull(links.open("q10.0.0.2", TestScope()))
        assertTrue(links.opened.isEmpty())
    }
}

/** A pipeline dependency these tests never reach: no link they open runs its sessions. */
private fun <T> unused(): Lazy<T> = lazy { error("not used by this test") }

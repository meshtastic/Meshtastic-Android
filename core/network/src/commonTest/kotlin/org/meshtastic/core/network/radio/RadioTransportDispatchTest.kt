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
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.testing.FakeBluetoothRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The prefix dispatch [BaseRadioTransportFactory] owns, with the platform hooks recorded rather than opened. */
class RadioTransportDispatchTest {

    private val service =
        mock<RadioInterfaceService>(MockMode.autofill).also { every { it.serviceScope } returns TestScope() }

    private class RecordingFactory :
        BaseRadioTransportFactory(
            bluetoothRepository = FakeBluetoothRepository(),
            virtual = object : VirtualRadioSessionFactory {
                override fun demo(address: String, service: RadioInterfaceService): RadioTransport =
                    NopRadioTransport("demo:$address")

                override fun replay(address: String, frames: ByteArray, service: RadioInterfaceService): RadioTransport =
                    error("Replay should fall back to demo when no asset is bundled")
            },
        ) {
        override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.TCP)

        override val mockTransportEnabled = MutableStateFlow(true)

        override val isReplayTransportAvailable: Boolean = false

        val hooks = mutableListOf<String>()

        override fun createBleTransport(address: String, service: RadioInterfaceService): RadioTransport =
            error("Unexpected BLE link")

        override fun createWebSocketTransport(url: String, service: RadioInterfaceService): RadioTransport =
            error("Unexpected WebSocket link")

        fun dispatch(address: String, service: RadioInterfaceService): RadioTransport =
            createPlatformTransport(address, service)

        override fun createTcpTransport(service: RadioInterfaceService, rest: String): RadioTransport {
            hooks += "tcp:$rest"
            return NopRadioTransport(rest)
        }

        override fun createSerialTransport(service: RadioInterfaceService, rest: String): RadioTransport {
            hooks += "serial:$rest"
            return NopRadioTransport(rest)
        }
    }

    private val factory = RecordingFactory()

    @Test
    fun `mock prefix creates a mock transport with the rest of the address`() {
        val transport = factory.dispatch("mshowcase", service)

        assertEquals("demo:showcase", assertIs<NopRadioTransport>(transport).address)
    }

    @Test
    fun `replay falls back to a mock where no capture is bundled`() {
        val transport = factory.dispatch("rcapture", service)

        assertEquals("demo:capture", assertIs<NopRadioTransport>(transport).address)
    }

    @Test
    fun `nop prefix creates an inert transport`() {
        val transport = factory.dispatch("nanything", service)

        assertEquals("anything", assertIs<NopRadioTransport>(transport).address)
    }

    @Test
    fun `an unknown prefix creates an inert transport holding the address minus its first char`() {
        val transport = factory.dispatch("q10.0.0.2", service)

        assertEquals("10.0.0.2", assertIs<NopRadioTransport>(transport).address)
    }

    @Test
    fun `tcp and serial prefixes reach their hooks with the prefix stripped`() {
        factory.dispatch("t10.0.0.2:4403", service)
        factory.dispatch("s/dev/ttyACM0", service)

        assertEquals(listOf("tcp:10.0.0.2:4403", "serial:/dev/ttyACM0"), factory.hooks)
    }

    @Test
    fun `createTransport opens an inert transport for a no-device or unknown address instead of throwing`() {
        assertIs<NopRadioTransport>(factory.createTransport("nanything", service))
        assertIs<NopRadioTransport>(factory.createTransport("q10.0.0.2", service))
    }

    @Test
    fun `createTransport opens a mock for a replay address where no capture is bundled`() {
        assertEquals("demo:capture", assertIs<NopRadioTransport>(factory.createTransport("rcapture", service)).address)
    }
}

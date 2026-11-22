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
package org.meshtastic.desktop.radio

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.network.radio.DesktopSdkTransportFactory
import org.meshtastic.core.network.radio.VirtualRadioSessionFactory
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.testing.FakeBluetoothRepository
import org.meshtastic.core.testing.FakeRadioTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DesktopSdkTransportSelectionTest {
    @Test
    fun `SDK binding takes TCP WebSocket serial and BLE from the normal factory`() = runTest {
        val service = mock<RadioInterfaceService>(MockMode.autofill)
        val transport = FakeRadioTransport()
        val requested = mutableListOf<Pair<InterfaceId, String>>()
        val factory =
            DesktopRadioTransportFactory(
                FakeBluetoothRepository(),
                DesktopSdkTransportFactory { id, endpoint, session ->
                    assertSame(service, session)
                    requested += id to endpoint
                    transport
                },
                mock<VirtualRadioSessionFactory>(MockMode.autofill),
            )

        assertSame(transport, factory.createTransport("t127.0.0.1:4403", service))
        assertSame(transport, factory.createTransport("wws://127.0.0.1:4404", service))
        assertSame(transport, factory.createTransport("s/dev/tty.usbmodem1", service))
        assertSame(transport, factory.createTransport("!AA:BB:CC:DD:EE:FF", service))
        assertSame(transport, factory.createTransport("xAA:BB:CC:DD:EE:FF", service))
        assertEquals(
            listOf(
                InterfaceId.TCP to "127.0.0.1:4403",
                InterfaceId.WEBSOCKET to "ws://127.0.0.1:4404/api/v1/ws",
                InterfaceId.SERIAL to "/dev/tty.usbmodem1",
                InterfaceId.BLUETOOTH to "AA:BB:CC:DD:EE:FF",
                InterfaceId.BLUETOOTH to "AA:BB:CC:DD:EE:FF",
            ),
            requested,
        )
    }
}

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
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.network.radio.DesktopSdkTransportFactory
import org.meshtastic.core.network.radio.WebSocketRadioTransport
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.testing.FakeBluetoothRepository
import org.meshtastic.core.testing.FakeRadioTransport
import org.meshtastic.desktop.DesktopBuildConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class DesktopSdkTransportSelectionTest {
    @Test
    fun `debug SDK binding takes TCP WebSocket and serial from the normal factory`() = runTest {
        if (!DesktopBuildConfig.IS_DEBUG) return@runTest
        val service = mock<RadioInterfaceService>(MockMode.autofill)
        val transport = FakeRadioTransport()
        val requested = mutableListOf<Pair<InterfaceId, String>>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val factory =
            DesktopRadioTransportFactory(
                mock<BleScanner>(MockMode.autofill),
                FakeBluetoothRepository(),
                mock<BleConnectionFactory>(MockMode.autofill),
                CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher),
                DesktopSdkTransportFactory { id, endpoint, session ->
                    assertSame(service, session)
                    requested += id to endpoint
                    transport
                },
            )

        assertSame(transport, factory.createTransport("t127.0.0.1:4403", service))
        assertSame(transport, factory.createTransport("wws://127.0.0.1:4404", service))
        assertSame(transport, factory.createTransport("s/dev/tty.usbmodem1", service))
        assertEquals(
            listOf(
                InterfaceId.TCP to "127.0.0.1:4403",
                InterfaceId.WEBSOCKET to "ws://127.0.0.1:4404/api/v1/ws",
                InterfaceId.SERIAL to "/dev/tty.usbmodem1",
            ),
            requested,
        )
    }

    @Test
    fun `WebSocket falls back when the SDK declines the endpoint`() = runTest {
        val service = mock<RadioInterfaceService>(MockMode.autofill)
        every { service.serviceScope } returns backgroundScope
        val dispatcher = StandardTestDispatcher(testScheduler)
        val factory =
            DesktopRadioTransportFactory(
                mock<BleScanner>(MockMode.autofill),
                FakeBluetoothRepository(),
                mock<BleConnectionFactory>(MockMode.autofill),
                CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher),
                DesktopSdkTransportFactory { _, _, _ -> null },
            )

        val transport = factory.createTransport("wws://127.0.0.1:4404/api/v1/ws?token=synthetic", service)
        assertIs<WebSocketRadioTransport>(transport)
        transport.close()
    }
}

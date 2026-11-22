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
package org.meshtastic.feature.settings.navigation

import org.meshtastic.proto.Config.TransportConfig.Transport
import org.meshtastic.proto.DeviceMetadata
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ConfigRouteTest {

    @Test
    fun `a node listing transports with no Wi-Fi or Ethernet still gets the Transports screen`() {
        val metadata =
            DeviceMetadata.Builder()
                .also { wb ->
                    wb.hasWifi = false
                    wb.hasEthernet = false
                    wb.supported_transports = listOf(Transport.TRANSPORT_LORA, Transport.TRANSPORT_BLE_GATT)
                }
                .build()

        val routes = ConfigRoute.deviceConfigRoutes(metadata)

        assertContains(routes, ConfigRoute.TRANSPORTS)
        assertFalse(ConfigRoute.NETWORK in routes)
    }

    @Test
    fun `a node listing no transports gets no Transports screen`() {
        val firmware = DeviceMetadata.Builder().also { wb -> wb.hasWifi = true }.build()

        assertFalse(ConfigRoute.TRANSPORTS in ConfigRoute.deviceConfigRoutes(firmware))
        assertFalse(ConfigRoute.TRANSPORTS in ConfigRoute.deviceConfigRoutes(null))
    }
}

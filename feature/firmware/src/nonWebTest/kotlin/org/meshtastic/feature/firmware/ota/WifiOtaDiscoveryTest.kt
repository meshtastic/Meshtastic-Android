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
package org.meshtastic.feature.firmware.ota

import io.ktor.network.sockets.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WifiOtaDiscoveryTest {

    @Test
    fun `senderHost extracts host from UDP sender address`() {
        val address = InetSocketAddress("192.168.1.44", WifiOtaTransport.DEFAULT_PORT)

        assertEquals("192.168.1.44", WifiOtaDiscovery.senderHost(address))
    }

    @Test
    fun `Meshtastic OTA discovery beacon is identified`() {
        assertTrue(WifiOtaDiscovery.isOtaDiscoveryBeacon("Meshtastic_ABCD 1.2.3".encodeToByteArray()))
        assertTrue(WifiOtaDiscovery.isOtaDiscoveryBeacon("Meshtastic_01af v1.2-5-g8a2b3c".encodeToByteArray()))
    }

    @Test
    fun `unrelated port 3232 datagrams are ignored`() {
        assertFalse(WifiOtaDiscovery.isOtaDiscoveryBeacon(ByteArray(0)))
        assertFalse(WifiOtaDiscovery.isOtaDiscoveryBeacon("OK".encodeToByteArray()))
        assertFalse(WifiOtaDiscovery.isOtaDiscoveryBeacon("0 3232 1024 abcdef".encodeToByteArray()))
        assertFalse(WifiOtaDiscovery.isOtaDiscoveryBeacon("Meshtastic_OTA 1.2.3".encodeToByteArray()))
        assertFalse(WifiOtaDiscovery.isOtaDiscoveryBeacon("Meshtastic_ABCD".encodeToByteArray()))
    }
}

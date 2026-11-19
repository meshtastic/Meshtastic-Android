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
package org.meshtastic.feature.connections.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualTcpAddressTest {

    @Test
    fun defaultPortIsLeftOffTheAddress() {
        assertEquals("192.168.1.20", manualTcpAddress("192.168.1.20", "4403"))
    }

    @Test
    fun otherValidPortIsAppended() {
        assertEquals("192.168.1.20:4404", manualTcpAddress("192.168.1.20", "4404"))
        assertEquals("192.168.1.20:65535", manualTcpAddress("192.168.1.20", "65535"))
    }

    @Test
    fun portOutsideTheValidRangeIsRejected() {
        assertNull(manualTcpAddress("192.168.1.20", "0"))
        assertNull(manualTcpAddress("192.168.1.20", "65536"))
        assertNull(manualTcpAddress("192.168.1.20", "-1"))
    }

    @Test
    fun invalidHostIsRejected() {
        assertNull(manualTcpAddress("", "4403"))
    }

    @Test
    fun nonNumericPortFallsBackToTheDefault() {
        assertEquals("192.168.1.20", manualTcpAddress("192.168.1.20", "abc"))
    }

    @Test
    fun aHostIsATcpAddress() {
        assertEquals("t192.168.1.20:4404", manualAddress("192.168.1.20", "4404"))
    }

    @Test
    fun aWebSocketUrlIsTakenAsWrittenWithThePortFieldIgnored() {
        assertEquals("wws://192.168.1.20/api/v1/ws", manualAddress("ws://192.168.1.20/api/v1/ws", "4403"))
        assertEquals(
            "wwss://meshtastic.local:9443/api/v1/ws",
            manualAddress(" wss://meshtastic.local:9443/api/v1/ws ", ""),
        )
        assertEquals("wws://[fe80::1]:80/api/v1/ws", manualAddress("ws://[fe80::1]:80/api/v1/ws", "4403"))
    }

    @Test
    fun aWebSocketUrlWithoutAPathGetsTheFirmwareEndpoint() {
        assertEquals("wws://[fe80::1]:80/api/v1/ws", manualAddress("ws://[fe80::1]:80", "4403"))
    }

    @Test
    fun anUppercaseSchemeIsAWebSocketUrlNotAnInvalidHost() {
        assertEquals("wws://192.168.1.20/api/v1/ws", manualAddress("WS://192.168.1.20", "4403"))
        assertTrue(isWebSocketEntry(" WSS://host"))
        assertFalse(isWebSocketEntry("192.168.1.20"))
    }

    @Test
    fun aBareHostIsFirmwaresWebSocketWhereRawTcpCannotReach() {
        assertEquals("wws://192.168.1.20/api/v1/ws", manualAddress("192.168.1.20", "", webSocketScheme = "ws"))
        assertEquals("wwss://node.local:4404/api/v1/ws", manualAddress("node.local", "4404", webSocketScheme = "wss"))
        assertEquals("wws://[fe80::1]/api/v1/ws", manualAddress("fe80::1", "", webSocketScheme = "ws"))
        assertEquals("wws://[fd00::1]:80/api/v1/ws", manualAddress("[fd00::1]", "80", webSocketScheme = "ws"))
        assertNull(manualAddress("192.168.1.20", "70000", webSocketScheme = "ws"))
        assertNull(manualAddress("", "", webSocketScheme = "ws"))
    }

    @Test
    fun firmwaresTlsPortsTakeWssFromAPlainPage() {
        assertEquals("wwss://localhost:9443/api/v1/ws", manualAddress("localhost", "9443", webSocketScheme = "ws"))
        assertEquals("wwss://192.168.1.20:443/api/v1/ws", manualAddress("192.168.1.20", "443", webSocketScheme = "ws"))
        assertEquals("wws://192.168.1.20:80/api/v1/ws", manualAddress("192.168.1.20", "80", webSocketScheme = "ws"))
    }

    @Test
    fun aMalformedWebSocketUrlIsRejected() {
        assertNull(manualAddress("ws://", "4403"))
        assertNull(manualAddress("wss://host with space/api/v1/ws", "4403"))
        assertNull(manualAddress("ws://host:70000/api/v1/ws", "4403"))
        assertNull(manualAddress("ws://user@host/api/v1/ws", "4403"))
    }
}

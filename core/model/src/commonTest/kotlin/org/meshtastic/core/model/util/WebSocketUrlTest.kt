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
package org.meshtastic.core.model.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebSocketUrlTest {

    @Test
    fun `a URL with a path is kept as written`() {
        assertEquals("ws://192.168.1.20/api/v1/ws", normalizeWebSocketUrl("ws://192.168.1.20/api/v1/ws"))
        assertEquals(
            "wss://meshtastic.local:9443/proxy/ws",
            normalizeWebSocketUrl(" wss://meshtastic.local:9443/proxy/ws "),
        )
    }

    @Test
    fun `an empty or root path becomes the firmware endpoint`() {
        assertEquals("ws://[fe80::1]:80/api/v1/ws", normalizeWebSocketUrl("ws://[fe80::1]:80"))
        assertEquals("ws://192.168.1.20/api/v1/ws", normalizeWebSocketUrl("ws://192.168.1.20/"))
    }

    @Test
    fun `a query follows the path and does not stop the root path defaulting`() {
        assertEquals("ws://host/api/v1/ws?token=abc", normalizeWebSocketUrl("ws://host/?token=abc"))
        assertEquals("ws://host/api/v1/ws?token=abc", normalizeWebSocketUrl("ws://host?token=abc"))
        assertEquals("ws://host:8080/proxy/ws?token=abc", normalizeWebSocketUrl("ws://host:8080/proxy/ws?token=abc"))
    }

    @Test
    fun `the scheme matches in any case and is lowercased`() {
        assertEquals("wss://host/api/v1/ws", normalizeWebSocketUrl("WSS://host/api/v1/ws"))
        assertEquals("ws://Host:8080/api/v1/ws", normalizeWebSocketUrl("Ws://Host:8080"))
    }

    @Test
    fun `a port outside the valid range is rejected`() {
        assertNull(normalizeWebSocketUrl("ws://host:0/api/v1/ws"))
        assertNull(normalizeWebSocketUrl("ws://host:65536/api/v1/ws"))
        assertEquals("ws://host:65535/api/v1/ws", normalizeWebSocketUrl("ws://host:65535"))
    }

    @Test
    fun `userinfo is rejected`() {
        assertNull(normalizeWebSocketUrl("ws://user@host/api/v1/ws"))
        assertNull(normalizeWebSocketUrl("ws://user:pass@host/api/v1/ws"))
    }

    @Test
    fun `anything else is rejected`() {
        listOf("", "ws://", "ws://:80/api/v1/ws", "ws://a b/", "http://host/api/v1/ws", "ws://host/#frag", "host")
            .forEach { assertNull(normalizeWebSocketUrl(it), "'$it' should be rejected") }
    }
}

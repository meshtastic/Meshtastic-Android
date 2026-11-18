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
package org.meshtastic.desktop.mcp

import dev.mokkery.MockMode
import dev.mokkery.mock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopMcpServerTest {

    private val server =
        DesktopMcpServer(
            serviceRepository = mock(MockMode.autofill),
            radioController = mock(MockMode.autofill),
            radioInterfaceService = mock(MockMode.autofill),
            nodeRepository = mock(MockMode.autofill),
            packetRepository = mock(MockMode.autofill),
            sendMessageUseCase = mock(MockMode.autofill),
        )

    @AfterTest fun stop() = server.stop()

    @Test
    fun `a port is parsed only when it is a number in range`() {
        assertEquals(8765, parseMcpPort(" 8765 "))
        assertNull(parseMcpPort(null))
        assertNull(parseMcpPort(""))
        assertNull(parseMcpPort("http"))
        assertNull(parseMcpPort("0"))
        assertNull(parseMcpPort("65536"))
    }

    @Test
    fun `a bare MAC gets the Bluetooth prefix and other addresses pass through`() {
        assertEquals("x34:B7:DA:62:18:C5", normalizeMcpAddress(" 34:b7:da:62:18:c5 "))
        assertEquals("t192.168.1.20", normalizeMcpAddress("t192.168.1.20"))
        assertEquals("s/dev/ttyUSB0", normalizeMcpAddress("s/dev/ttyUSB0"))
    }

    @Test
    fun `initialize succeeds on loopback`() {
        val port = freePort()
        server.start(port)

        val status = post(port, host = "127.0.0.1:$port")

        assertEquals(HTTP_OK, status)
    }

    @Test
    fun `a request naming another host is refused`() {
        val port = freePort()
        server.start(port)

        val status = post(port, host = "attacker.example")

        assertTrue(status in HTTP_CLIENT_ERRORS, "expected a 4xx for a foreign Host, got $status")
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    /** Sends an MCP `initialize` with the given Host header over a raw socket and returns the HTTP status. */
    private fun post(port: Int, host: String): Int {
        val body =
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",""" +
                """"capabilities":{},"clientInfo":{"name":"test","version":"1"}}}"""
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val request =
                "POST /mcp HTTP/1.1\r\n" +
                    "Host: $host\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Accept: application/json, text/event-stream\r\n" +
                    "Content-Length: ${body.toByteArray().size}\r\n" +
                    "Connection: close\r\n\r\n" +
                    body
            socket.getOutputStream().apply {
                write(request.toByteArray())
                flush()
            }
            val statusLine = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
            return statusLine.split(" ")[1].toInt()
        }
    }

    private companion object {
        const val HTTP_OK = 200
        val HTTP_CLIENT_ERRORS = 400..499
        const val SOCKET_TIMEOUT_MS = 10_000
    }
}

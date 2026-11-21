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
package org.meshtastic.core.network.serial

import kotlinx.coroutines.test.runTest
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.TransportState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WebSerialRadioLinkTest {
    @Test
    fun `frames split across reads arrive in order then EOF ends the link`() = runTest {
        val port = fakePort()
        val registry = WebSerialPortRegistry().also { it.rebind("test", port) }
        val link = WebSerialRadioLink("test", registry)
        val received = mutableListOf<Frame>()

        try {
            link.connect()
            assertEquals(TransportState.Connected, link.state.value)
            assertFailsWith<MeshtasticException.Transport> {
                link.frames().collect { received += it }
            }
            assertEquals(2, received.size)
            assertContentEquals(byteArrayOf(0x94.toByte(), 0xC3.toByte(), 0, 2, 7, 8), received[0].bytes.toByteArray())
            assertContentEquals(byteArrayOf(0x94.toByte(), 0xC3.toByte(), 0, 1, 9), received[1].bytes.toByteArray())

            link.send(received.last())
            assertEquals(9, writtenPayload(port))
        } finally {
            link.disconnect()
        }

        assertEquals(TransportState.Disconnected, link.state.value)
        assertTrue(isClosed(port))
    }
}

private fun fakePort(): JsSerialPort = js(
    """(() => {
        const chunks = [[0x94, 0xc3, 0, 2, 7], [8, 0x94, 0xc3, 0, 1, 9]];
        const port = { readLocked: false, writeLocked: false, closed: false, sent: null };
        port.open = async () => {};
        port.close = async () => {
            if (port.readLocked || port.writeLocked) throw new Error('Port still locked');
            port.closed = true;
        };
        port.readable = { getReader: () => {
            port.readLocked = true;
            return {
                read: async () => chunks.length
                    ? { done: false, value: new Uint8Array(chunks.shift()) }
                    : { done: true },
                cancel: async () => {},
                releaseLock: () => { port.readLocked = false; },
            };
        }};
        port.writable = { getWriter: () => {
            port.writeLocked = true;
            return {
                write: async (bytes) => { port.sent = bytes; },
                releaseLock: () => { port.writeLocked = false; },
            };
        }};
        return port;
    })()""",
)

@Suppress("UnusedParameter")
private fun isClosed(port: JsSerialPort): Boolean = js("port.closed")

@Suppress("UnusedParameter")
private fun writtenPayload(port: JsSerialPort): Int = js("port.sent[4]")

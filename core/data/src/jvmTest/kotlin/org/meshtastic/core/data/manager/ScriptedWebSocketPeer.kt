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
package org.meshtastic.core.data.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.meshtastic.sdk.TransportState
import org.meshtastic.sdk.transport.websocket.WebSocketTransport
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

/** What the peer does with each WebSocket client, in the ways firmware's WebSocket phone API ends a session. */
internal sealed interface PeerScript {
    /** Answers the upgrade with [status] instead of 101. */
    data class RefuseUpgrade(val status: Int) : PeerScript

    /** Completes the upgrade, then closes with [code]. */
    data class CloseWith(val code: Int) : PeerScript

    /** Completes the upgrade, then drops the socket without a close frame. */
    data object Drop : PeerScript

    /** Accepts the TCP connection and answers a TLS hello with plain text. */
    data object PlainTextToTls : PeerScript
}

/** A local socket that ends every WebSocket client the SDK's production transport opens as [script] says. */
internal class ScriptedWebSocketPeer(private val script: PeerScript) : AutoCloseable {
    private val server = ServerSocket(0, BACKLOG, InetAddress.getByName("127.0.0.1"))
    val port: Int = server.localPort

    private val worker = Thread {
        while (!server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            runCatching { client.use { serve(it) } }
        }
    }
        .apply {
            isDaemon = true
            start()
        }

    private fun serve(client: Socket) {
        val output = client.getOutputStream()
        if (script == PeerScript.PlainTextToTls) {
            output.write("HTTP/1.1 400 Bad Request\r\n\r\n".toByteArray())
            output.flush()
            return
        }
        val input = client.getInputStream().bufferedReader()
        var key = ""
        while (true) {
            val line = input.readLine() ?: return
            if (line.isEmpty()) break
            if (line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) key = line.substringAfter(':').trim()
        }
        when (script) {
            is PeerScript.RefuseUpgrade ->
                output.respond("HTTP/1.1 ${script.status} Refused\r\nContent-Length: 0\r\n\r\n")

            is PeerScript.CloseWith -> {
                output.upgrade(key)
                // RFC 6455 5.5.1: FIN plus opcode 0x8, an unmasked two-byte payload holding the close code.
                output.write(byteArrayOf(CLOSE_OPCODE, 2, (script.code shr 8).toByte(), script.code.toByte()))
                output.flush()
                // Hold the socket until the client answers the close and hangs up.
                client.getInputStream().transferTo(OutputStream.nullOutputStream())
            }

            PeerScript.Drop -> output.upgrade(key)

            PeerScript.PlainTextToTls -> Unit
        }
    }

    private fun OutputStream.upgrade(key: String) {
        val accept =
            Base64.getEncoder()
                .encodeToString(MessageDigest.getInstance("SHA-1").digest((key + WEBSOCKET_GUID).toByteArray()))
        respond(
            "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n\r\n",
        )
    }

    private fun OutputStream.respond(text: String) {
        write(text.toByteArray())
        flush()
    }

    override fun close() {
        server.close()
        worker.join(JOIN_MS)
    }

    private companion object {
        const val BACKLOG = 4
        const val JOIN_MS = 1_000L
        const val CLOSE_OPCODE = 0x88.toByte()
        const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }
}

/** The error the SDK's WebSocketTransport publishes when [script] ends a session at [path]. */
internal suspend fun sessionEndFor(script: PeerScript, path: String = "/api/v1/ws"): TransportState.Error =
    ScriptedWebSocketPeer(script).use { peer ->
        val scheme = if (script == PeerScript.PlainTextToTls) "wss" else "ws"
        transportEnd("$scheme://127.0.0.1:${peer.port}$path")
    }

/** The error the SDK's WebSocketTransport publishes for [url]: a failed connect, or the end of an opened session. */
internal suspend fun transportEnd(url: String): TransportState.Error = withContext(Dispatchers.IO) {
    val transport = WebSocketTransport(url)
    try {
        withTimeout(10.seconds) {
            runCatching { transport.connect() }
            transport.state.first { it is TransportState.Error } as TransportState.Error
        }
    } finally {
        transport.disconnect()
    }
}

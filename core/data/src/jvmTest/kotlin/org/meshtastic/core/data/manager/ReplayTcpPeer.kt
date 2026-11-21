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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Buffer
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.WireFraming
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicReference

/** Exposes the scripted PhoneAPI fixture as a TCP peer for the SDK's production transport. */
internal suspend fun withReplayTcpPeer(exercise: suspend (port: Int) -> Unit) = coroutineScope {
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
        val accepted = AtomicReference<Socket>()
        val peer =
            launch(Dispatchers.IO) {
                try {
                    server.accept().use { socket ->
                        accepted.set(socket)
                        val scripted =
                            SdkRoomReplayTest.SdkReplayTransport(
                                this,
                                "replay:tcp",
                                SdkRoomReplayTest.replayAsset(),
                            )
                        scripted.connect()
                        val output = socket.getOutputStream()
                        val writer = launch {
                            scripted.frames().collect {
                                output.write(it.bytes.toByteArray())
                                output.flush()
                            }
                        }
                        try {
                            val input = DataInputStream(socket.getInputStream())
                            while (true) scripted.send(input.readFrame() ?: break)
                        } finally {
                            writer.cancelAndJoin()
                            scripted.disconnect()
                        }
                    }
                } catch (closed: SocketException) {
                    ensureActive()
                    throw closed
                }
            }
        try {
            exercise(server.localPort)
        } finally {
            withContext(NonCancellable) {
                peer.cancel()
                accepted.get()?.close()
                server.close()
                peer.join()
            }
        }
    }
}

private fun DataInputStream.readFrame(): Frame? {
    // The SDK transport and engine can both send wake bytes before framing starts.
    var sawPrefix = false
    while (true) {
        val next = read()
        if (next == -1) return null
        if (sawPrefix && next.toByte() == WireFraming.MAGIC_1) break
        sawPrefix = next.toByte() == WireFraming.MAGIC_0
    }
    val size = readUnsignedShort()
    check(size <= WireFraming.MAX_PAYLOAD_SIZE)
    val payload = ByteArray(size).also(::readFully)
    val frame =
        Buffer()
            .writeByte(WireFraming.MAGIC_0.toInt())
            .writeByte(WireFraming.MAGIC_1.toInt())
            .writeShort(size)
            .write(payload)
    return Frame(frame.readByteString())
}

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
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LockdownStatus
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.ToRadio
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.WireFraming
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals

/** Exposes the scripted PhoneAPI fixture as a TCP peer for the SDK's production transport. */
internal suspend fun withReplayTcpPeer(lockdownPassphrase: String? = null, exercise: suspend (port: Int) -> Unit) =
    coroutineScope {
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
                                if (lockdownPassphrase != null) input.unlock(output, lockdownPassphrase)
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

private fun DataInputStream.unlock(output: OutputStream, passphrase: String) {
    fun request(): ToRadio = ToRadio.ADAPTER.decode(checkNotNull(readFrame()).bytes.substring(WireFraming.HEADER_SIZE))

    val config = request()
    assertEquals(69420, config.want_config_id)
    val node = MyNodeInfo.Builder().also { it.my_node_num = SdkRoomReplayTest.LOCAL_NODE }.build()
    output.sendEnvelope(FromRadio.Builder().also { it.my_info = node }.build())
    output.sendEnvelope(FromRadio.Builder().also { it.config_complete_id = config.want_config_id }.build())
    output.sendLockdownState(LockdownStatus.State.LOCKED)
    val packet = checkNotNull(request().packet)
    assertEquals(node.my_node_num, packet.to)
    val admin = AdminMessage.ADAPTER.decode(checkNotNull(packet.decoded).payload)
    assertEquals(passphrase, admin.lockdown_auth?.passphrase?.utf8())
    output.sendLockdownState(LockdownStatus.State.UNLOCKED)
}

private fun OutputStream.sendLockdownState(state: LockdownStatus.State) {
    val status = LockdownStatus.Builder().also { it.state = state }.build()
    sendEnvelope(FromRadio.Builder().also { it.lockdown_status = status }.build())
}

private fun OutputStream.sendEnvelope(envelope: FromRadio) {
    val payload = envelope.encode()
    val frame =
        Buffer()
            .writeByte(WireFraming.MAGIC_0.toInt())
            .writeByte(WireFraming.MAGIC_1.toInt())
            .writeShort(payload.size)
            .write(payload)
    write(frame.readByteArray())
    flush()
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

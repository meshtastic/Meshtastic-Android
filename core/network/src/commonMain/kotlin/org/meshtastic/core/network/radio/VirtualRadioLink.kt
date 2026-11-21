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
package org.meshtastic.core.network.radio

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import okio.Buffer
import org.meshtastic.proto.ToRadio
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.RadioTransport
import org.meshtastic.sdk.TransportState
import org.meshtastic.sdk.WireFraming

/** Firmware-side stream framing for the app’s simulated peers. */
abstract class VirtualRadioLink : RadioTransport {
    private val inbound = Channel<Frame>(BUFFER_CAPACITY)
    private val linkState = MutableStateFlow<TransportState>(TransportState.Disconnected)
    final override val state: StateFlow<TransportState> = linkState

    final override fun frames() = inbound.receiveAsFlow()

    protected fun connected() {
        linkState.value = TransportState.Connected
    }

    protected fun disconnected() {
        linkState.value = TransportState.Disconnected
        inbound.close()
    }

    protected fun publish(payload: ByteArray) {
        val result = inbound.trySend(virtualPhoneApiFrame(payload))
        if (result.isFailure && !result.isClosed) {
            inbound.close(MeshtasticException.Transport("Virtual radio receive buffer overflowed"))
        }
    }

    protected suspend fun emitFrame(payload: ByteArray) = inbound.send(virtualPhoneApiFrame(payload))

    @Suppress("ReturnCount")
    final override suspend fun send(frame: Frame) {
        check(state.value == TransportState.Connected) { "Virtual radio is disconnected" }
        val bytes = frame.bytes
        val start = WireFraming.HEADER_SIZE
        if (bytes.size < start || bytes[0] != WireFraming.MAGIC_0 || bytes[1] != WireFraming.MAGIC_1) return
        val length =
            ((bytes[2].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or (bytes[LENGTH_LOW_BYTE].toInt() and BYTE_MASK)
        if (length != bytes.size - start) return
        val request = runCatching { ToRadio.ADAPTER.decode(bytes.substring(start)) }.getOrNull() ?: return
        respond(request)
    }

    protected abstract fun respond(request: ToRadio)

    private companion object {
        const val BUFFER_CAPACITY = 8192
    }
}

internal fun virtualPhoneApiFrame(payload: ByteArray): Frame = Frame(
    Buffer()
        .writeByte(WireFraming.MAGIC_0.toInt())
        .writeByte(WireFraming.MAGIC_1.toInt())
        .writeShort(payload.size)
        .write(payload)
        .readByteString(),
)

private const val BYTE_MASK = 0xff

/** Index of the frame header's low length byte. */
private const val LENGTH_LOW_BYTE = 3

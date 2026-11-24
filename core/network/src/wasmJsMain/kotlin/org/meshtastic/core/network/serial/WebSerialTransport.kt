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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.RadioTransport
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.TransportState
import org.meshtastic.sdk.transport.serial.SerialFrameAssembler
import kotlin.time.Duration.Companion.seconds

/** Browser I/O for an existing port grant. The SDK owns framing and PhoneAPI session state. */
class WebSerialTransport(private val portId: String, private val registry: WebSerialPortRegistry) : RadioTransport {
    override val identity = TransportIdentity("serial-web:$portId")

    private val linkState = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: StateFlow<TransportState> = linkState

    private var port: JsSerialPort? = null
    private var reader: JsReadableStreamDefaultReader? = null
    private var writer: JsWritableStreamDefaultWriter? = null

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    override suspend fun connect() {
        linkState.value = TransportState.Connecting
        try {
            val candidate =
                registry.find(portId) ?: throw MeshtasticException.Transport("Web Serial port is unavailable")
            // The browser open() promise cannot be cancelled. Record the port before observing cancellation.
            withContext(NonCancellable) {
                candidate.openSuspend(BAUD_RATE)
                port = candidate
            }
            currentCoroutineContext().ensureActive()
            reader = checkNotNull(candidate.readable).getReader()
            writer = checkNotNull(candidate.writable).getWriter()
            linkState.value = TransportState.Connected
        } catch (e: CancellationException) {
            disconnect()
            throw e
        } catch (e: Exception) {
            disconnect()
            throw MeshtasticException.Transport("Could not open the Web Serial port", e)
        }
    }

    override fun frames(): Flow<Frame> = flow {
        val input = reader ?: throw MeshtasticException.Transport("Web Serial input is closed")
        val decoded = mutableListOf<Frame>()
        val assembler = SerialFrameAssembler { decoded.add(it) }
        while (true) {
            val result = input.readSuspend()
            if (result.done) throw MeshtasticException.Transport("Web Serial port reached end of stream")
            result.valueAsByteArray()?.let { assembler.feed(it) }
            for (frame in decoded) emit(frame)
            decoded.clear()
        }
    }

    override suspend fun send(frame: Frame) {
        val output = writer ?: throw MeshtasticException.Transport("Web Serial output is closed")
        output.writeBytes(frame.bytes.toByteArray())
    }

    override suspend fun disconnect() = withContext(NonCancellable) {
        val opened = port
        val input = reader
        val output = writer
        port = null
        reader = null
        writer = null
        withTimeoutOrNull(CLOSE_TIMEOUT) { runCatching { input?.cancelSuspend() } }
        runCatching { input?.releaseLock() }
        runCatching { output?.releaseLock() }
        withTimeoutOrNull(CLOSE_TIMEOUT) { runCatching { opened?.closeSuspend() } }
        linkState.value = TransportState.Disconnected
    }

    private companion object {
        const val BAUD_RATE = 115_200
        val CLOSE_TIMEOUT = 3.seconds
    }
}

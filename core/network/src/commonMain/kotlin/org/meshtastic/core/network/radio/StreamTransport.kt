/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback

/**
 * An interface that assumes we are talking to a meshtastic device over some sort of stream connection (serial or TCP
 * probably).
 *
 * Delegates framing logic to [StreamFrameCodec] from `core:network`.
 */
abstract class StreamTransport(protected val callback: RadioTransportCallback, protected val scope: CoroutineScope) :
    RadioTransport {

    private val codec =
        StreamFrameCodec(onPacketReceived = { callback.handleFromRadio(it) }, logTag = "StreamTransport")

    override suspend fun close() {
        Logger.d { "Closing stream for good" }
        onDeviceDisconnect(true)
    }

    /**
     * Notify the transport callback that our device has gone away, but wait for it to come back.
     *
     * @param waitForStopped if true we should wait for the transport to finish - must be false if called from inside
     *   transport callbacks
     * @param isPermanent true if the device is definitely gone (e.g. USB unplugged), false if it may come back (e.g.
     *   TCP transient disconnect). Defaults to true for serial — subclasses may override with false.
     */
    protected open fun onDeviceDisconnect(waitForStopped: Boolean, isPermanent: Boolean = true) {
        callback.onDisconnect(isPermanent = isPermanent)
    }

    protected open fun connect() {
        // Before connecting, send a few START1s to wake a sleeping device
        sendBytes(StreamFrameCodec.WAKE_BYTES)

        // Now tell clients they can (finally use the api)
        callback.onConnect()
    }

    /** Writes raw bytes to the underlying stream (serial port, TCP socket, etc.). */
    abstract fun sendBytes(p: ByteArray)

    /** Flushes buffered bytes to the underlying stream. No-op by default. */
    open fun flushBytes() {}

    override fun handleSendToRadio(p: ByteArray) {
        // This method is called from a continuation and it might show up late, so check for uart being null
        scope.handledLaunch { codec.frameAndSend(p, ::sendBytes, ::flushBytes) }
    }

    /** Process a single incoming byte through the stream framing state machine. */
    protected fun readChar(c: Byte) {
        codec.processInputByte(c)
    }
}

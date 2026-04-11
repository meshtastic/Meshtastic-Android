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
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport

/**
 * An interface that assumes we are talking to a meshtastic device over some sort of stream connection (serial or TCP
 * probably).
 *
 * Delegates framing logic to [StreamFrameCodec] from `core:network`.
 */
abstract class StreamInterface(protected val service: RadioInterfaceService) : RadioTransport {

    private val codec = StreamFrameCodec(onPacketReceived = { service.handleFromRadio(it) }, logTag = "StreamInterface")

    override fun close() {
        Logger.d { "Closing stream for good" }
        onDeviceDisconnect(true)
    }

    /**
     * Tell MeshService our device has gone away, but wait for it to come back
     *
     * @param waitForStopped if true we should wait for the manager to finish - must be false if called from inside the
     *   manager callbacks
     * @param isPermanent true if the device is definitely gone (e.g. USB unplugged), false if it may come back (e.g.
     *   TCP transient disconnect). Defaults to true for serial — subclasses like [TCPInterface] override with false.
     */
    protected open fun onDeviceDisconnect(waitForStopped: Boolean, isPermanent: Boolean = true) {
        service.onDisconnect(isPermanent = isPermanent)
    }

    protected open fun connect() {
        // Before telling mesh service, send a few START1s to wake a sleeping device
        sendBytes(StreamFrameCodec.WAKE_BYTES)

        // Now tell clients they can (finally use the api)
        service.onConnect()
    }

    abstract fun sendBytes(p: ByteArray)

    // If subclasses need to flush at the end of a packet they can implement
    open fun flushBytes() {}

    override fun handleSendToRadio(p: ByteArray) {
        // This method is called from a continuation and it might show up late, so check for uart being null
        service.serviceScope.handledLaunch { codec.frameAndSend(p, ::sendBytes, ::flushBytes) }
    }

    /** Process a single incoming byte through the stream framing state machine. */
    protected fun readChar(c: Byte) {
        codec.processInputByte(c)
    }
}

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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.core.repository.TransportDisconnectReason
import kotlin.time.Duration.Companion.seconds

private val STREAM_CLOSE_DRAIN_TIMEOUT = 10.seconds

/**
 * An interface that assumes we are talking to a meshtastic device over some sort of stream connection (serial or TCP
 * probably).
 *
 * Delegates framing logic to [StreamFrameCodec] from `core:network`.
 */
abstract class StreamTransport(protected val callback: RadioTransportCallback, protected val scope: CoroutineScope) :
    RadioTransport {

    private class FramedSend(
        val payload: ByteArray,
        val writer: suspend (ByteArray) -> Unit,
        val flusher: suspend () -> Unit,
        val onCompletion: () -> Unit,
    )

    private val codec =
        StreamFrameCodec(onPacketReceived = { callback.handleFromRadio(it) }, logTag = "StreamTransport")
    private val sendQueue = Channel<FramedSend>(capacity = MAX_PENDING_SENDS)
    private val sendWorker =
        scope
            .handledLaunch {
                for (send in sendQueue) {
                    val failure =
                        runCatching { codec.frameAndSend(send.payload, send.writer, send.flusher) }.exceptionOrNull()
                    try {
                        when (failure) {
                            null -> Unit

                            is CancellationException ->
                                if (!currentCoroutineContext().isActive) {
                                    throw failure
                                } else {
                                    Logger.w(failure) { "StreamTransport: framed send cancelled" }
                                }

                            is Error -> throw failure

                            else -> Logger.w(failure) { "StreamTransport: framed send failed" }
                        }
                    } finally {
                        completeSend(send)
                    }
                }
            }
            .also { worker -> worker.invokeOnCompletion { drainAbandonedSends() } }

    private fun drainAbandonedSends() {
        sendQueue.close()
        while (true) {
            val abandoned = sendQueue.tryReceive().getOrNull() ?: break
            completeSend(abandoned)
        }
    }

    /**
     * Closes admission and lets already-admitted frames finish before cancelling the worker as a bounded fallback.
     *
     * A forced cancellation can interrupt a frame mid-write, so [close] is terminal for the underlying stream: a
     * subclass must close or replace that stream before another transport instance writes to it.
     */
    override suspend fun close() {
        Logger.d { "Closing stream transport" }
        sendQueue.close()
        val drained = withTimeoutOrNull(STREAM_CLOSE_DRAIN_TIMEOUT) { sendWorker.join() } != null
        if (!drained) {
            Logger.w { "StreamTransport: framed send drain timed out after $STREAM_CLOSE_DRAIN_TIMEOUT" }
            sendWorker.cancelAndJoin()
        }
    }

    /**
     * Signals the transport callback that the device has disconnected and optionally waits for the transport to stop.
     *
     * @param waitForStopped if true we should wait for the transport to finish - must be false if called from inside
     *   transport callbacks
     * @param isPermanent true when the user explicitly disconnects (e.g. [close] was called), or when an authorization
     *   failure makes the current connection attempt unrecoverable. USB unplug, I/O errors, and similar conditions are
     *   transient — the transport may recover when the device is replugged or the OS re-enumerates. Defaults to false
     *   so callbacks default to "may come back".
     * @param errorMessage optional user-facing reason for the disconnect; surfaced via
     *   [RadioTransportCallback.onDisconnect]. Prefer [reason] for newly-classified disconnect causes so transports do
     *   not own UI copy.
     * @param reason optional structured cause for service/UI-specific handling.
     */
    protected open fun onDeviceDisconnect(
        waitForStopped: Boolean,
        isPermanent: Boolean = false,
        errorMessage: String? = null,
        reason: TransportDisconnectReason? = null,
    ) {
        callback.onDisconnect(isPermanent = isPermanent, errorMessage = errorMessage, reason = reason)
    }

    protected open fun connect() {
        // Before connecting, send a few START1s to wake a sleeping device
        sendBytes(StreamFrameCodec.WAKE_BYTES)

        // Now tell clients they can (finally use the api)
        callback.onConnect()
    }

    /**
     * Writes raw bytes to the underlying stream (serial port, TCP socket, etc.). Implementations may block until the
     * driver accepts the bytes, so direct callers must already be running on an I/O dispatcher. Framed packet sends use
     * [queueFramedSend], whose writer controls dispatcher confinement. Subclasses that rely on [queueFramedSend]'s
     * default writer must therefore supply an I/O-confined [scope].
     */
    abstract fun sendBytes(p: ByteArray)

    /** Flushes buffered bytes to the underlying stream. No-op by default. */
    open fun flushBytes() {}

    /**
     * Queues the framed packet onto [scope], optionally binding the deferred write to a transport-session resource.
     *
     * Capturing the writer at admission keeps a queued write from being redirected to a replacement connection before
     * its coroutine runs. The result reports only successful scheduling on a live scope; physical delivery is not
     * confirmed.
     *
     * [onCompletion] runs exactly once whether the send is admitted, abandoned during worker teardown, or rejected.
     */
    protected fun queueFramedSend(
        payload: ByteArray,
        writer: suspend (ByteArray) -> Unit = { sendBytes(it) },
        flusher: suspend () -> Unit = { flushBytes() },
        onCompletion: () -> Unit = {},
    ): Boolean {
        val send = FramedSend(payload, writer, flusher, onCompletion)
        val accepted = sendWorker.isActive && sendQueue.trySend(send).isSuccess
        if (!accepted) completeSend(send)
        return accepted
    }

    private fun completeSend(send: FramedSend) {
        val failure = runCatching(send.onCompletion).exceptionOrNull()
        when (failure) {
            null -> Unit
            is Error -> throw failure
            else -> Logger.e(failure) { "StreamTransport: framed-send completion failed" }
        }
    }

    override fun handleSendToRadio(p: ByteArray): Boolean = queueFramedSend(p)

    /** Process a single incoming byte through the stream framing state machine. */
    protected fun readChar(c: Byte) {
        codec.processInputByte(c)
    }

    internal companion object {
        /** Upper bound on framed sends awaiting the serialized writer. */
        const val MAX_PENDING_SENDS = 32
    }
}

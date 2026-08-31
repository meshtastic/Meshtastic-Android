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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.network.serial.JsReadableStreamDefaultReader
import org.meshtastic.core.network.serial.JsSerialPort
import org.meshtastic.core.network.serial.JsWritableStreamDefaultWriter
import org.meshtastic.core.network.serial.WebSerialPortRegistry
import org.meshtastic.core.network.serial.cancelSuspend
import org.meshtastic.core.network.serial.closeSuspend
import org.meshtastic.core.network.serial.openSuspend
import org.meshtastic.core.network.serial.readSuspend
import org.meshtastic.core.network.serial.valueAsByteArray
import org.meshtastic.core.network.serial.writeBytes
import org.meshtastic.core.network.transport.HeartbeatSender
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.repository.RadioTransportCallback
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_BAUD_RATE = 115200
private val SERIAL_START_JOIN_TIMEOUT = 10.seconds

/**
 * wasmJs [RadioTransport] backed by the Web Serial API (`navigator.serial`).
 *
 * The granted [JsSerialPort] handle is resolved from [registry] by [portId] — this transport never calls
 * `requestPort()` itself, since that requires an active user gesture (see [WebSerialPortRegistry]'s KDoc). If [portId]
 * is unknown to the registry (most commonly after a page reload — Web Serial grants are not restorable via any address
 * string alone, only [WebSerialPortRegistry.refreshGrantedPorts] re-establishes them), [start] reports a disconnect
 * immediately rather than silently doing nothing.
 *
 * Mirrors `org.meshtastic.core.network.SerialTransport` (JVM/jSerialComm)'s shape: single connection generation, no
 * hot-plug re-binding within one instance. Uses [StreamTransport] for START1/START2 packet framing.
 *
 * Unlike the JVM and Android serial transports, this class needs no locks or atomics around its connection state:
 * wasmJs is single-threaded, so the only concurrency here is cooperative coroutine interleaving, and Kotlin's ordinary
 * sequencing already prevents interleaved code from observing a half-updated `port`/`reader`/`writer`.
 */
class WasmJsSerialTransport(
    private val portId: String,
    private val registry: WebSerialPortRegistry,
    callback: RadioTransportCallback,
    scope: CoroutineScope,
) : StreamTransport(callback, scope) {
    private val lifecycle = TransportLifecycleGate("wasmJs serial")
    private var port: JsSerialPort? = null
    private var reader: JsReadableStreamDefaultReader? = null
    private var writer: JsWritableStreamDefaultWriter? = null
    private var startJob: Job? = null
    private var readJob: Job? = null
    private val heartbeatSender = HeartbeatSender(sendToRadio = { handleSendToRadio(it) }, logTag = "Serial[$portId]")

    override fun start() {
        if (startJob?.isActive == true || port != null) {
            Logger.d { "[$portId] Ignoring start while serial connection is already active" }
            return
        }
        startJob =
            scope.handledLaunch {
                val lease = lifecycle.tryAcquire() ?: return@handledLaunch
                try {
                    if (!connectPort()) {
                        Logger.w { "[$portId] Serial port could not be opened; signalling disconnect" }
                        callback.onDisconnect(isPermanent = false, errorMessage = "Could not open serial port")
                    }
                } finally {
                    lease.release()
                }
            }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun connectPort(): Boolean {
        val candidate = registry.get(portId)
        if (candidate == null) {
            Logger.w { "[$portId] Serial port not found in this session's granted list" }
            return false
        }
        return try {
            candidate.openSuspend(DEFAULT_BAUD_RATE)
            val readable = candidate.readable
            val writable = candidate.writable
            if (readable == null || writable == null) {
                Logger.w { "[$portId] Serial port opened but exposed no readable/writable stream" }
                runCatching { candidate.closeSuspend() }
                false
            } else {
                Logger.i { "[$portId] Serial port opened (baud=$DEFAULT_BAUD_RATE)" }
                port = candidate
                val activeWriter = writable.getWriter()
                writer = activeWriter
                reader = readable.getReader()
                activeWriter.writeBytes(StreamFrameCodec.WAKE_BYTES)
                callback.onConnect()
                startReadLoop()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "[$portId] Serial connect failed" }
            false
        }
    }

    private fun startReadLoop() {
        readJob = scope.handledLaunch { runReadLoop() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runReadLoop() {
        val activeReader = reader ?: return
        try {
            while (currentCoroutineContext().isActive) {
                val result = activeReader.readSuspend()
                if (result.done) break
                result.valueAsByteArray()?.forEach(::readChar)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "[$portId] Serial read error" }
        } finally {
            runCatching { activeReader.releaseLock() }
        }
        Logger.d { "[$portId] Serial read loop exiting" }
        if (!lifecycle.isClosed) onDeviceDisconnect(waitForStopped = true, isPermanent = false)
    }

    override fun handleSendToRadio(p: ByteArray): Boolean {
        val lease = lifecycle.tryAcquire() ?: return false
        val activeWriter = writer
        return if (activeWriter == null) {
            lease.release()
            Logger.w { "[$portId] Serial connection not available, cannot send ${p.size} bytes" }
            false
        } else {
            queueFramedSend(
                payload = p,
                writer = { bytes -> activeWriter.writeBytes(bytes) },
                onCompletion = lease::release,
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun sendBytes(p: ByteArray) {
        val activeWriter = writer
        if (activeWriter == null) {
            Logger.w { "[$portId] Serial connection not available, cannot send ${p.size} bytes" }
            return
        }
        scope.handledLaunch {
            try {
                activeWriter.writeBytes(p)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "[$portId] Serial write failed" }
            }
        }
    }

    override fun flushBytes() {
        // Web Streams' write() already returns only once the underlying stream has accepted the chunk; there is no
        // separate flush concept to expose.
    }

    override fun keepAlive() {
        scope.handledLaunch { heartbeatSender.sendHeartbeat() }
    }

    override suspend fun close() {
        withContext(NonCancellable) {
            Logger.d { "[$portId] Closing serial transport" }
            startJob?.cancel()
            startJob?.let { job -> withTimeoutOrNull(SERIAL_START_JOIN_TIMEOUT) { job.join() } }
            super.close()
            val completed =
                lifecycle.close {
                    // Cancel the reader first so a pending native read() resolves promptly and the read loop's own
                    // exception handling unwinds it, instead of a raw coroutine cancellation cutting it off mid-await.
                    runCatching { reader?.cancelSuspend() }
                    readJob?.cancelAndJoin()
                    runCatching { writer?.closeSuspend() }
                    runCatching { port?.closeSuspend() }
                }
            if (!completed) Logger.w { "[$portId] wasmJs serial teardown did not complete within its lifecycle bounds" }
        }
    }
}

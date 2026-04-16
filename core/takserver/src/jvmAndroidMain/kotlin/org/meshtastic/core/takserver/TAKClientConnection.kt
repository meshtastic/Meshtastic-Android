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
@file:Suppress("TooManyFunctions", "TooGenericExceptionCaught")

package org.meshtastic.core.takserver

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.isActive as coroutineIsActive

/**
 * Per-client state machine for a connected TAK client (ATAK / iTAK / WinTAK).
 *
 * This is the jvmAndroidMain implementation, using plain `java.net.Socket` (which is also
 * the base class of [javax.net.ssl.SSLSocket] from [TAKServerJvm]) with blocking
 * `InputStream`/`OutputStream` I/O wrapped in [Dispatchers.IO] coroutines.
 *
 * Responsibilities:
 *  - TAK protocol negotiation handshake (`t-x-takp-v` / `-q` / `-r`)
 *  - Read loop that frames `<event>` elements off the stream via [CoTXmlFrameBuffer]
 *  - Keepalive loop that emits a `t-x-d-d` event every [TAK_KEEPALIVE_INTERVAL_MS]
 *  - Serializing writes under a mutex so interleaved broadcasts never corrupt the XML stream
 *  - Lifecycle reporting up to [TAKServerJvm] via [onEvent] (`Connected`, `Disconnected`,
 *    `Error`, `ClientInfoUpdated`, `Message`)
 */
internal class TAKClientConnection(
    private val socket: Socket,
    val clientInfo: TAKClientInfo,
    private val onEvent: (TAKConnectionEvent) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private var currentClientInfo = clientInfo
    private val frameBuffer = CoTXmlFrameBuffer()

    private val inputStream: InputStream = socket.getInputStream()
    // Wrap the OutputStream in a BufferedOutputStream so that multiple small writes
    // (we emit a full XML event per write) coalesce into one syscall; flush() after
    // each event to push the bytes through TLS immediately.
    private val outputStream: OutputStream = BufferedOutputStream(socket.getOutputStream())
    private val writeMutex = Mutex()

    /**
     * Per-connection child scope. Every coroutine this class launches — the read loop,
     * the keepalive loop, and every single send — is attached to [connectionScope] so
     * that [emitDisconnected] can tear the whole connection down with one
     * `connectionScope.cancel()`.
     *
     * Why this is critical: [broadcast] in [TAKServerJvm] fires `connection.send()` on
     * **every** connected client for **every** CoT event coming off the mesh (and with
     * a 56-node nodeDB each `nodeDBbyNum` emission fans out to ~56 broadcasts). If
     * [sendXml] launched those writes on the server-level [scope] — as the previous
     * implementation did — a single dead connection could accumulate hundreds of
     * in-flight write coroutines before it was removed from [TAKServerJvm.connections],
     * and every one of them would spin up, hit the closed TLS socket, and log
     * `SocketException: Socket closed` from `BufferedOutputStream.flush()`. Scoping
     * writes to [connectionScope] means cancelling the scope wipes the entire backlog.
     *
     * Uses a [SupervisorJob] child of [scope]'s job so a single write failure doesn't
     * cascade-cancel other connections on the same server.
     */
    private val connectionScope: CoroutineScope =
        CoroutineScope(SupervisorJob(scope.coroutineContext[Job]) + ioDispatcher)

    /** Guards against emitting [TAKConnectionEvent.Disconnected] more than once. */
    private val disconnectedEmitted = AtomicBoolean(false)

    /**
     * Fail-fast flag checked at the top of [sendXml] so racing broadcasts against a
     * dead connection don't even allocate a coroutine.
     */
    @Volatile private var closed = false

    fun start() {
        onEvent(TAKConnectionEvent.Connected(currentClientInfo))
        sendProtocolSupport()

        connectionScope.launch { readLoop() }
        connectionScope.launch { keepaliveLoop() }
    }

    private fun sendProtocolSupport() {
        val serverUid = "Meshtastic-TAK-Server-${Random.nextInt().toString(TAK_HEX_RADIX)}"
        val now = Clock.System.now()
        val stale = now + TAK_KEEPALIVE_INTERVAL_MS.milliseconds
        val detail =
            """
            <TakControl>
                <TakProtocolSupport version="0"/>
            </TakControl>
            """
                .trimIndent()
        sendXmlInternal(buildEventXml(uid = serverUid, type = "t-x-takp-v", now = now, stale = stale, detail = detail))
    }

    private suspend fun readLoop() {
        try {
            val buffer = ByteArray(TAK_XML_READ_BUFFER_SIZE)
            while (connectionScope.coroutineIsActive && !closed && !socket.isClosed) {
                // Blocking read off the TLS input stream — must run on the IO dispatcher.
                val bytesRead = withContext(ioDispatcher) { inputStream.read(buffer) }
                if (bytesRead > 0) {
                    processReceivedData(buffer.copyOfRange(0, bytesRead))
                } else if (bytesRead == -1) {
                    break // EOF: remote peer closed the connection cleanly
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!closed) {
                Logger.w(e) { "TAK client read error: ${currentClientInfo.id}" }
                emitDisconnected(TAKConnectionEvent.Error(e))
            }
            return
        }
        emitDisconnected(TAKConnectionEvent.Disconnected)
    }

    private suspend fun keepaliveLoop() {
        while (connectionScope.coroutineIsActive && !closed && !socket.isClosed) {
            kotlinx.coroutines.delay(TAK_KEEPALIVE_INTERVAL_MS)
            if (closed) break
            sendKeepalive()
        }
    }

    private fun sendKeepalive() {
        val now = Clock.System.now()
        val stale = now + TAK_KEEPALIVE_INTERVAL_MS.milliseconds
        sendXmlInternal(buildEventXml(uid = "takPong", type = "t-x-d-d", now = now, stale = stale, detail = ""))
    }

    /** Respond to ATAK's `t-x-c-t` ping with a pong to reset its RX timeout. */
    private fun sendPong() {
        val now = Clock.System.now()
        val stale = now + TAK_KEEPALIVE_INTERVAL_MS.milliseconds
        sendXmlInternal(buildEventXml(uid = "takPong", type = "t-x-c-t-r", now = now, stale = stale, detail = ""))
    }

    private fun processReceivedData(newData: ByteArray) {
        frameBuffer.append(newData).forEach { xmlString -> parseAndHandleMessage(xmlString) }
    }

    private fun parseAndHandleMessage(xmlString: String) {
        // Fast-path: detect keepalive pings before full XML parsing to avoid
        // both the parse overhead and the noisy RAW CoT IN log line every 4.5s.
        if (xmlString.contains("t-x-c-t") || xmlString.contains("uid=\"ping\"")) {
            sendPong()
            return
        }

        // Full raw CoT XML from the ATAK client, before any parsing happens.
        // Emitted at debug level so it's always available in logcat for field
        // debugging without needing a release rebuild. Not truncated — the
        // reader of this log needs the complete event to reproduce issues.
        Logger.d { "RAW CoT IN (TCP ${currentClientInfo.id}): $xmlString" }

        val parser = CoTXmlParser(xmlString)
        val result = parser.parse()

        result.onSuccess { cotMessage ->
            when {
                cotMessage.type.startsWith("t-x-takp") -> {
                    handleProtocolControl(cotMessage.type, xmlString)
                    return
                }
                else -> {
                    cotMessage.contact?.let { contact ->
                        val updatedClientInfo =
                            currentClientInfo.copy(
                                callsign = currentClientInfo.callsign ?: contact.callsign,
                                uid = currentClientInfo.uid ?: cotMessage.uid,
                            )
                        if (updatedClientInfo != currentClientInfo) {
                            currentClientInfo = updatedClientInfo
                            onEvent(TAKConnectionEvent.ClientInfoUpdated(updatedClientInfo))
                        }
                    }
                    onEvent(TAKConnectionEvent.Message(cotMessage, currentClientInfo))
                }
            }
        }
    }

    private fun handleProtocolControl(type: String, xmlString: String) {
        if (type == "t-x-takp-q") {
            sendProtocolResponse()
        } else {
            Logger.d { "Unhandled protocol control type: $type (raw=$xmlString)" }
        }
    }

    private fun sendProtocolResponse() {
        val serverUid = "Meshtastic-TAK-Server-${Random.nextInt().toString(TAK_HEX_RADIX)}"
        val now = Clock.System.now()
        val stale = now + TAK_KEEPALIVE_INTERVAL_MS.milliseconds
        val detail =
            """
            <TakControl>
                <TakResponse status="true"/>
            </TakControl>
            """
                .trimIndent()
        sendXmlInternal(buildEventXml(uid = serverUid, type = "t-x-takp-r", now = now, stale = stale, detail = detail))
    }

    fun send(cotMessage: CoTMessage) {
        if (closed) return
        val xml = cotMessage.toXml()
        // Full raw CoT XML being shipped out to the ATAK client, after the
        // CoTMessage → XML round trip. This is the exact bytes the client
        // will receive, so logging here closes the debugging loop with the
        // matching RAW CoT IN line on the receiver.
        Logger.d { "RAW CoT OUT (TCP ${currentClientInfo.id}): $xml" }
        sendXmlInternal(xml)
    }

    private fun buildEventXml(uid: String, type: String, now: Instant, stale: Instant, detail: String): String {
        val detailContent = if (detail.isBlank()) "<detail/>" else "<detail>$detail</detail>"
        val point = """<point lat="0" lon="0" hae="0" ce="$TAK_UNKNOWN_POINT_VALUE" le="$TAK_UNKNOWN_POINT_VALUE"/>"""
        return """<event version="2.0" uid="$uid" type="$type" time="$now" start="$now" stale="$stale" how="m-g">""" +
            point +
            detailContent +
            "</event>"
    }

    /** Send raw XML directly to this client. Used for mesh-originated messages
     *  that bypass CoTMessage parsing to preserve shape detail elements. */
    fun sendRawXml(xml: String) {
        Logger.d { "RAW CoT OUT (TCP ${currentClientInfo.id}): [raw] $xml" }
        sendXmlInternal(xml)
    }

    private fun sendXmlInternal(xml: String) {
        // Fail-fast synchronous check BEFORE allocating a coroutine. This is the hot path
        // for broadcasts — see the scope doc above for why it matters.
        if (closed) return
        connectionScope.launch {
            // Re-check inside the coroutine: we may have been cancelled or marked closed
            // between the launch and the dispatcher picking this up.
            if (closed) return@launch
            try {
                writeMutex.withLock {
                    if (closed || socket.isClosed) return@withLock
                    val bytes = xml.toByteArray(Charsets.UTF_8)
                    // Blocking write on TLS output must run on the IO dispatcher
                    withContext(ioDispatcher) {
                        outputStream.write(bytes)
                        outputStream.flush()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Don't spam on writes that raced a disconnect we already observed.
                if (!closed) {
                    Logger.w(e) { "TAK client send error: ${currentClientInfo.id}" }
                    emitDisconnected(TAKConnectionEvent.Error(e))
                }
            }
        }
    }

    fun close() {
        frameBuffer.clear()
        emitDisconnected(TAKConnectionEvent.Disconnected)
    }

    /**
     * Emits [event] (expected to be [TAKConnectionEvent.Disconnected] or [TAKConnectionEvent.Error]) at most once
     * across all code paths, then tears down the per-connection coroutines and socket.
     *
     * This is the ONLY place the connection's entire coroutine scope — keepalive loop,
     * read loop, and any in-flight send coroutines — gets cancelled when the *remote*
     * peer closes the TLS stream. Without this, Java's [Socket.isClosed] only reports
     * whether *our* side called close(), so the keepalive loop's `!socket.isClosed`
     * guard never fires, the broadcast fanout keeps launching writes onto the dead
     * socket via [sendXml], and every iteration logs `SSLOutputStream / Socket closed`.
     * Before [closed] + [connectionScope.cancel] were added, a single session with a
     * few reconnects accumulated hundreds of zombie write coroutines each spamming
     * errors in parallel.
     *
     * Idempotent via [AtomicBoolean.compareAndSet], so racing calls from [readLoop],
     * [keepaliveLoop], and [sendXml] all converge on a single teardown.
     */
    private fun emitDisconnected(event: TAKConnectionEvent) {
        if (disconnectedEmitted.compareAndSet(false, true)) {
            // Set the fail-fast flag BEFORE emitting the event. [TAKServerJvm] will
            // schedule an async map removal on receipt, and any broadcast racing the
            // removal must see `closed = true` when it hits [send] / [sendXml].
            closed = true
            onEvent(event)
            // Cancel the whole scope — readLoop, keepaliveLoop, and every queued or
            // in-flight sendXml coroutine. Any write blocked in the syscall will throw
            // on the next iteration because we close the socket next.
            connectionScope.cancel()
            runCatching { socket.close() }
        }
    }
}

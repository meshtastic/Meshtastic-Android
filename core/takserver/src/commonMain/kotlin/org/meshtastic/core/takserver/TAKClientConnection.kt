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
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.isActive as coroutineIsActive

class TAKClientConnection(
    private val socket: Socket,
    val clientInfo: TAKClientInfo,
    private val onEvent: (TAKConnectionEvent) -> Unit,
    private val scope: CoroutineScope,
) {
    private var currentClientInfo = clientInfo
    private val frameBuffer = CoTXmlFrameBuffer()

    private val readChannel: ByteReadChannel = socket.openReadChannel()
    private val writeChannel: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
    private val writeMutex = Mutex()

    /** Tracks the last time data was received from the client, used for idle timeout detection. */
    @Volatile private var lastDataReceived: Instant = Clock.System.now()

    /** Guards against emitting [TAKConnectionEvent.Disconnected] more than once. */
    @Volatile private var disconnectedEmitted = false

    fun start() {
        onEvent(TAKConnectionEvent.Connected(currentClientInfo))
        sendProtocolSupport()

        scope.launch { readLoop() }

        scope.launch { keepaliveLoop() }
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
        sendXml(buildEventXml(uid = serverUid, type = "t-x-takp-v", now = now, stale = stale, detail = detail))
    }

    private suspend fun readLoop() {
        try {
            val buffer = ByteArray(TAK_XML_READ_BUFFER_SIZE)
            while (scope.coroutineIsActive && !socket.isClosed) {
                // Suspend until data is available — no polling delay needed
                readChannel.awaitContent()
                val bytesRead = readChannel.readAvailable(buffer)
                if (bytesRead > 0) {
                    lastDataReceived = Clock.System.now()
                    processReceivedData(buffer.copyOfRange(0, bytesRead))
                } else if (bytesRead == -1) {
                    break // EOF
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "TAK client read error: ${currentClientInfo.id}" }
            emitDisconnected(TAKConnectionEvent.Error(e))
            return
        }
        emitDisconnected(TAKConnectionEvent.Disconnected)
    }

    private suspend fun keepaliveLoop() {
        val idleTimeoutMs = TAK_KEEPALIVE_INTERVAL_MS * TAK_READ_IDLE_TIMEOUT_MULTIPLIER
        while (scope.coroutineIsActive && !socket.isClosed) {
            kotlinx.coroutines.delay(TAK_KEEPALIVE_INTERVAL_MS)

            val idleMs = (Clock.System.now() - lastDataReceived).inWholeMilliseconds
            if (idleMs > idleTimeoutMs) {
                Logger.w {
                    "TAK client ${currentClientInfo.id} idle for ${idleMs}ms " +
                        "(threshold ${idleTimeoutMs}ms), closing connection"
                }
                close()
                return
            }

            sendKeepalive()
        }
    }

    private fun sendKeepalive() {
        val now = Clock.System.now()
        val stale = now + (TAK_KEEPALIVE_INTERVAL_MS * TAK_KEEPALIVE_STALE_MULTIPLIER).milliseconds
        sendXml(buildEventXml(uid = "takPong", type = "t-x-c-t", now = now, stale = stale, detail = ""))
    }

    private fun sendPong() {
        val now = Clock.System.now()
        val stale = now + (TAK_KEEPALIVE_INTERVAL_MS * TAK_KEEPALIVE_STALE_MULTIPLIER).milliseconds
        sendXml(buildEventXml(uid = "takPong", type = "t-x-c-t-r", now = now, stale = stale, detail = ""))
    }

    private fun processReceivedData(newData: ByteArray) {
        // frameBuffer.append returns List<String> — pass directly without re-encoding
        frameBuffer.append(newData).forEach { xmlString -> parseAndHandleMessage(xmlString) }
    }

    private fun parseAndHandleMessage(xmlString: String) {
        // Parse first, then filter on the structured type field to avoid false positives
        val parser = CoTXmlParser(xmlString)
        val result = parser.parse()

        result.onSuccess { cotMessage ->
            when {
                cotMessage.type.startsWith("t-x-takp") -> {
                    handleProtocolControl(cotMessage.type, xmlString)
                    return
                }

                cotMessage.type == "t-x-c-t" || cotMessage.uid == "ping" -> {
                    sendPong()
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

                    onEvent(TAKConnectionEvent.Message(cotMessage))
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
        sendXml(buildEventXml(uid = serverUid, type = "t-x-takp-r", now = now, stale = stale, detail = detail))
    }

    fun send(cotMessage: CoTMessage) {
        val xml = cotMessage.toXml()
        sendXml(xml)
    }

    private fun buildEventXml(uid: String, type: String, now: Instant, stale: Instant, detail: String): String {
        val detailContent = if (detail.isBlank()) "<detail/>" else "<detail>$detail</detail>"
        val point = """<point lat="0" lon="0" hae="0" ce="$TAK_UNKNOWN_POINT_VALUE" le="$TAK_UNKNOWN_POINT_VALUE"/>"""
        return """<event version="2.0" uid="$uid" type="$type" time="$now" start="$now" stale="$stale" how="m-g">""" +
            point +
            detailContent +
            "</event>"
    }

    private fun sendXml(xml: String) {
        scope.launch {
            try {
                writeMutex.withLock {
                    if (!socket.isClosed) {
                        writeChannel.writeStringUtf8(xml)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "TAK client send error: ${currentClientInfo.id}" }
                close()
            }
        }
    }

    fun close() {
        frameBuffer.clear()
        try {
            socket.close()
        } catch (e: Exception) {
            Logger.w(e) { "Error closing TAK client socket: ${currentClientInfo.id}" }
        }
        emitDisconnected(TAKConnectionEvent.Disconnected)
    }

    /**
     * Emits [event] (expected to be [TAKConnectionEvent.Disconnected] or [TAKConnectionEvent.Error]) at most once
     * across all code paths.
     */
    private fun emitDisconnected(event: TAKConnectionEvent) {
        if (!disconnectedEmitted) {
            disconnectedEmitted = true
            onEvent(event)
        }
    }
}

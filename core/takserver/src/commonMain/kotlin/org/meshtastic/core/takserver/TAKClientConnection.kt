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

        val xml =
            """
            <event version="2.0" uid="$serverUid" type="t-x-takp-v" time="$now" start="$now" stale="$stale" how="m-g">
                <point lat="0" lon="0" hae="0" ce="$TAK_UNKNOWN_POINT_VALUE" le="$TAK_UNKNOWN_POINT_VALUE"/>
                <detail>
                    <TakControl>
                        <TakProtocolSupport version="0"/>
                    </TakControl>
                </detail>
            </event>
        """
                .trimIndent()

        sendXml(xml)
    }

    private suspend fun readLoop() {
        try {
            val buffer = ByteArray(TAK_XML_READ_BUFFER_SIZE)
            while (scope.coroutineIsActive && !socket.isClosed) {
                // Suspend until data is available — no polling delay needed
                readChannel.awaitContent()
                val bytesRead = readChannel.readAvailable(buffer)
                if (bytesRead > 0) {
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
        while (scope.coroutineIsActive && !socket.isClosed) {
            kotlinx.coroutines.delay(TAK_KEEPALIVE_INTERVAL_MS)
            sendKeepalive()
        }
    }

    private fun sendKeepalive() {
        val now = Clock.System.now()
        val stale = now + TAK_KEEPALIVE_INTERVAL_MS.milliseconds
        val xml =
            """
            <event version="2.0" uid="takPong" type="t-x-d-d" time="$now" start="$now" stale="$stale" how="m-g">
                <point lat="0" lon="0" hae="0" ce="$TAK_UNKNOWN_POINT_VALUE" le="$TAK_UNKNOWN_POINT_VALUE"/>
                <detail/>
            </event>
        """
                .trimIndent()

        sendXml(xml)
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
                    // Keepalive / ping — discard silently
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
            sendProtocolResponse(true)
        } else {
            Logger.d { "Unhandled protocol control type: $type (raw=$xmlString)" }
        }
    }

    private fun sendProtocolResponse(accepted: Boolean) {
        val serverUid = "Meshtastic-TAK-Server-${Random.nextInt().toString(TAK_HEX_RADIX)}"
        val now = Clock.System.now()
        val stale = now + TAK_KEEPALIVE_INTERVAL_MS.milliseconds

        val xml =
            """
            <event version="2.0" uid="$serverUid" type="t-x-takp-r" time="$now" start="$now" stale="$stale" how="m-g">
                <point lat="0" lon="0" hae="0" ce="$TAK_UNKNOWN_POINT_VALUE" le="$TAK_UNKNOWN_POINT_VALUE"/>
                <detail>
                    <TakControl>
                        <TakResponse status="${if (accepted) "true" else "false"}"/>
                    </TakControl>
                </detail>
            </event>
        """
                .trimIndent()

        sendXml(xml)
    }

    fun send(cotMessage: CoTMessage) {
        val xml = cotMessage.toXml()
        sendXml(xml)
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

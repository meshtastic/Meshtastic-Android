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
@file:Suppress("TooManyFunctions", "TooGenericExceptionCaught", "SwallowedException")

package org.meshtastic.core.takserver

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Clock
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

    fun start() {
        onEvent(TAKConnectionEvent.Connected(currentClientInfo))
        sendProtocolSupport()

        scope.launch { readLoop() }

        scope.launch { keepaliveLoop() }
    }

    private fun sendProtocolSupport() {
        val serverUid = "Meshtastic-TAK-Server-${Random.nextInt().toString(TAK_HEX_RADIX)}"
        val now = Clock.System.now().toString()

        val xml =
            """
            <event version="2.0" uid="$serverUid" type="t-x-takp-v" time="$now" start="$now" stale="$now" how="m-g">
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
                val bytesRead = readChannel.readAvailable(buffer)
                if (bytesRead > 0) {
                    processReceivedData(buffer.copyOfRange(0, bytesRead))
                } else if (bytesRead == -1) {
                    break // EOF
                }
                delay(TAK_READ_LOOP_DELAY_MS)
            }
        } catch (e: Exception) {
            // Handle error
        }
        onEvent(TAKConnectionEvent.Disconnected)
    }

    private suspend fun keepaliveLoop() {
        while (scope.coroutineIsActive && !socket.isClosed) {
            delay(TAK_KEEPALIVE_INTERVAL_MS)
            sendKeepalive()
        }
    }

    private fun sendKeepalive() {
        val now = Clock.System.now().toString()
        val xml =
            """
            <event version="2.0" uid="takPong" type="t-x-d-d" time="$now" start="$now" stale="$now" how="m-g">
                <point lat="0" lon="0" hae="0" ce="$TAK_UNKNOWN_POINT_VALUE" le="$TAK_UNKNOWN_POINT_VALUE"/>
                <detail/>
            </event>
        """
                .trimIndent()

        sendXml(xml)
    }

    private fun processReceivedData(newData: ByteArray) {
        frameBuffer.append(newData).forEach { xmlMessage -> parseAndHandleMessage(xmlMessage.encodeToByteArray()) }
    }

    private fun parseAndHandleMessage(data: ByteArray) {
        val xmlString = data.decodeToString()

        if (xmlString.contains("t-x-takp")) {
            handleProtocolControl(xmlString)
            return
        }

        if (xmlString.contains("t-x-c-t") || xmlString.contains("uid=\"ping\"")) {
            return
        }

        val parser = CoTXmlParser(xmlString)
        val result = parser.parse()

        result.onSuccess { cotMessage ->
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

    private fun handleProtocolControl(xmlString: String) {
        if (xmlString.contains("t-x-takp-q")) {
            sendProtocolResponse(true)
        }
    }

    private fun sendProtocolResponse(accepted: Boolean) {
        val serverUid = "Meshtastic-TAK-Server-${Random.nextInt().toString(TAK_HEX_RADIX)}"
        val now = Clock.System.now().toString()

        val xml =
            """
            <event version="2.0" uid="$serverUid" type="t-x-takp-r" time="$now" start="$now" stale="$now" how="m-g">
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
        try {
            scope.launch {
                writeMutex.withLock {
                    if (!socket.isClosed) {
                        writeChannel.writeStringUtf8(xml)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun close() {
        frameBuffer.clear()
        try {
            socket.close()
        } catch (e: Exception) {
            // Ignore
        }
        onEvent(TAKConnectionEvent.Disconnected)
    }
}

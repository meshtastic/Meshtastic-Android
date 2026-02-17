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
package com.geeksville.mesh.repository.radio

import co.touchlab.kermit.Logger
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.repository.network.NetworkRepository
import com.geeksville.mesh.util.Exceptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.ToRadio
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException

open class TCPInterface
@AssistedInject
constructor(
    service: RadioInterfaceService,
    private val dispatchers: CoroutineDispatchers,
    @Assisted private val address: String,
) : StreamInterface(service) {

    companion object {
        const val MAX_RETRIES_ALLOWED = Int.MAX_VALUE
        const val MIN_BACKOFF_MILLIS = 1 * 1000L // 1 second
        const val MAX_BACKOFF_MILLIS = 5 * 60 * 1000L // 5 minutes
        const val SOCKET_TIMEOUT = 5000
        const val SOCKET_RETRIES = 18
        const val SERVICE_PORT = NetworkRepository.SERVICE_PORT
        const val TIMEOUT_LOG_INTERVAL = 5 // Log every Nth timeout
    }

    private var retryCount = 1
    private var backoffDelay = MIN_BACKOFF_MILLIS

    private var socket: Socket? = null
    private var outStream: OutputStream? = null

    private var connectionStartTime: Long = 0
    private var packetsReceived: Int = 0
    private var packetsSent: Int = 0
    private var bytesReceived: Long = 0
    private var bytesSent: Long = 0
    private var timeoutEvents: Int = 0

    init {
        connect()
    }

    override fun sendBytes(p: ByteArray) {
        val stream = outStream
        if (stream == null) {
            Logger.w { "[$address] TCP cannot send ${p.size} bytes: outStream is null (connection not established)" }
            return
        }

        packetsSent++
        bytesSent += p.size
        Logger.d { "[$address] TCP sending packet #$packetsSent - ${p.size} bytes (Total TX: $bytesSent bytes)" }
        try {
            stream.write(p)
        } catch (ex: IOException) {
            Logger.e(ex) { "[$address] TCP write error: ${ex.message}" }
            onDeviceDisconnect(false)
        }
    }

    override fun flushBytes() {
        val stream = outStream ?: return
        Logger.d { "[$address] TCP flushing output stream" }
        try {
            stream.flush()
        } catch (ex: IOException) {
            Logger.e(ex) { "[$address] TCP flush error: ${ex.message}" }
            onDeviceDisconnect(false)
        }
    }

    override fun onDeviceDisconnect(waitForStopped: Boolean) {
        val s = socket
        if (s != null) {
            val uptime =
                if (connectionStartTime > 0) {
                    nowMillis - connectionStartTime
                } else {
                    0
                }
            Logger.w {
                "[$address] TCP disconnecting - " +
                    "Uptime: ${uptime}ms, " +
                    "Packets RX: $packetsReceived ($bytesReceived bytes), " +
                    "Packets TX: $packetsSent ($bytesSent bytes), " +
                    "Timeout events: $timeoutEvents"
            }
            s.close()
            socket = null
            outStream = null
        }
        super.onDeviceDisconnect(waitForStopped)
    }

    override fun connect() {
        service.serviceScope.handledLaunch {
            while (true) {
                try {
                    startConnect()
                } catch (ex: IOException) {
                    val uptime =
                        if (connectionStartTime > 0) {
                            nowMillis - connectionStartTime
                        } else {
                            0
                        }
                    // Connection failures are common when the radio is offline or out of range
                    Logger.w(ex) { "[$address] TCP connection error after ${uptime}ms - ${ex.message}" }
                    onDeviceDisconnect(false)
                } catch (ex: Throwable) {
                    val uptime =
                        if (connectionStartTime > 0) {
                            nowMillis - connectionStartTime
                        } else {
                            0
                        }
                    Logger.e(ex) { "[$address] TCP exception after ${uptime}ms - ${ex.message}" }
                    Exceptions.report(ex, "Exception in TCP reader")
                    onDeviceDisconnect(false)
                }

                if (retryCount > MAX_RETRIES_ALLOWED) {
                    Logger.e { "[$address] TCP max retries ($MAX_RETRIES_ALLOWED) exceeded, giving up" }
                    break
                }

                Logger.i {
                    "[$address] TCP reconnect attempt #$retryCount in ${backoffDelay / 1000}s " +
                        "(backoff: ${backoffDelay}ms)"
                }
                delay(backoffDelay)

                retryCount++
                backoffDelay = minOf(backoffDelay * 2, MAX_BACKOFF_MILLIS)
            }
            Logger.i { "[$address] TCP reader exiting" }
        }
    }

    override fun keepAlive() {
        Logger.d { "[$address] TCP keepAlive" }
        val heartbeat = ToRadio(heartbeat = Heartbeat())
        handleSendToRadio(heartbeat.encode())
    }

    // Create a socket to make the connection with the server
    private suspend fun startConnect() = withContext(dispatchers.io) {
        val attemptStart = nowMillis
        Logger.i { "[$address] TCP connection attempt starting..." }

        val parts = address.split(":", limit = 2)
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: SERVICE_PORT

        Logger.d { "[$address] Resolving host '$host' and connecting to port $port..." }

        Socket(InetAddress.getByName(host), port).use { socket ->
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = SOCKET_TIMEOUT
            this@TCPInterface.socket = socket

            val connectTime = nowMillis - attemptStart
            connectionStartTime = nowMillis
            Logger.i {
                "[$address] TCP socket connected in ${connectTime}ms - " +
                    "Local: ${socket.localSocketAddress}, Remote: ${socket.remoteSocketAddress}"
            }

            BufferedOutputStream(socket.getOutputStream()).use { outputStream ->
                outStream = outputStream

                BufferedInputStream(socket.getInputStream()).use { inputStream ->
                    super.connect()

                    retryCount = 1
                    backoffDelay = MIN_BACKOFF_MILLIS

                    var timeoutCount = 0
                    while (timeoutCount < SOCKET_RETRIES) {
                        try { // close after 90s of inactivity
                            val c = inputStream.read()
                            if (c == -1) {
                                Logger.w {
                                    "[$address] TCP got EOF on stream after $packetsReceived packets received"
                                }
                                break
                            } else {
                                timeoutCount = 0
                                packetsReceived++
                                bytesReceived++
                                readChar(c.toByte())
                            }
                        } catch (ex: SocketTimeoutException) {
                            timeoutCount++
                            timeoutEvents++
                            if (timeoutCount % TIMEOUT_LOG_INTERVAL == 0) {
                                Logger.d {
                                    "[$address] TCP socket timeout count: $timeoutCount/$SOCKET_RETRIES " +
                                        "(total timeouts: $timeoutEvents)"
                                }
                            }
                            // Ignore and start another read
                        }
                    }
                    if (timeoutCount >= SOCKET_RETRIES) {
                        val inactivityMs = SOCKET_RETRIES * SOCKET_TIMEOUT
                        Logger.w {
                            "[$address] TCP closing connection due to $SOCKET_RETRIES consecutive timeouts " +
                                "(${inactivityMs}ms of inactivity)"
                        }
                    }
                }
            }
            onDeviceDisconnect(false)
        }
    }
}

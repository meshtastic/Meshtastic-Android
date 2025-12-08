/*
 * Copyright (c) 2025 Meshtastic LLC
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

import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.repository.network.NetworkRepository
import com.geeksville.mesh.util.Exceptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException

class TCPInterface @AssistedInject constructor(service: RadioInterfaceService, @Assisted private val address: String) :
    StreamInterface(service) {

    companion object {
        const val MAX_RETRIES_ALLOWED = Int.MAX_VALUE
        const val MIN_BACKOFF_MILLIS = 1 * 1000L // 1 second
        const val MAX_BACKOFF_MILLIS = 5 * 60 * 1000L // 5 minutes
        const val SOCKET_TIMEOUT = 5000
        const val SOCKET_RETRIES = 18
        const val SERVICE_PORT = NetworkRepository.SERVICE_PORT
    }

    private var retryCount = 1
    private var backoffDelay = MIN_BACKOFF_MILLIS

    private var socket: Socket? = null
    private lateinit var outStream: OutputStream

    init {
        connect()
    }

    override fun sendBytes(p: ByteArray) {
        outStream.write(p)
    }

    override fun flushBytes() {
        outStream.flush()
    }

    override fun onDeviceDisconnect(waitForStopped: Boolean) {
        val s = socket
        if (s != null) {
            Timber.d("Closing TCP socket")
            s.close()
            socket = null
        }
        super.onDeviceDisconnect(waitForStopped)
    }

    override fun connect() {
        service.serviceScope.handledLaunch {
            while (true) {
                try {
                    startConnect()
                } catch (ex: IOException) {
                    Timber.e("IOException in TCP reader: $ex")
                    onDeviceDisconnect(false)
                } catch (ex: Throwable) {
                    Exceptions.report(ex, "Exception in TCP reader")
                    onDeviceDisconnect(false)
                }

                if (retryCount > MAX_RETRIES_ALLOWED) break

                Timber.d("Reconnect attempt $retryCount in ${backoffDelay / 1000}s")
                delay(backoffDelay)

                retryCount++
                backoffDelay = minOf(backoffDelay * 2, MAX_BACKOFF_MILLIS)
            }
            Timber.d("Exiting TCP reader")
        }
    }

    // Create a socket to make the connection with the server
    private suspend fun startConnect() = withContext(Dispatchers.IO) {
        Timber.d("TCP connecting to $address")

        val (host, port) =
            address.split(":", limit = 2).let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: SERVICE_PORT) }

        Socket(InetAddress.getByName(host), port).use { socket ->
            socket.tcpNoDelay = true
            socket.soTimeout = SOCKET_TIMEOUT
            this@TCPInterface.socket = socket

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
                                Timber.w("Got EOF on TCP stream")
                                break
                            } else {
                                timeoutCount = 0
                                readChar(c.toByte())
                            }
                        } catch (ex: SocketTimeoutException) {
                            timeoutCount++
                            // Ignore and start another read
                        }
                    }
                }
            }
            onDeviceDisconnect(false)
        }
    }
}

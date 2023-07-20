package com.geeksville.mesh.repository.radio

import android.content.Context
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.repository.usb.UsbRepository
import com.geeksville.mesh.util.Exceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException

class TCPInterface(service: RadioInterfaceService, private val address: String) :
    StreamInterface(service) {

    companion object : Logging, InterfaceFactory('t') {
        override fun createInterface(
            context: Context,
            service: RadioInterfaceService,
            usbRepository: UsbRepository, // Temporary until dependency injection transition is completed
            rest: String
        ): IRadioInterface = TCPInterface(service, rest)

        init {
            registerFactory()
        }
    }

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
            debug("Closing TCP socket")
            s.close()
            socket = null
        }
        super.onDeviceDisconnect(waitForStopped)
    }

    override fun connect() {
        service.serviceScope.handledLaunch {
            try {
                startConnect()
            } catch (ex: IOException) {
                errormsg("IOException in TCP reader: $ex")
                onDeviceDisconnect(false)
            } catch (ex: Throwable) {
                Exceptions.report(ex, "Exception in TCP reader")
                onDeviceDisconnect(false)
            }
            debug("Exiting TCP reader")
        }
    }

    // Create a socket to make the connection with the server
    private suspend fun startConnect() = withContext(Dispatchers.IO) {
        debug("TCP connecting to $address")
        Socket(InetAddress.getByName(address), 4403).use { socket ->
            socket.tcpNoDelay = true
            socket.soTimeout = 500
            this@TCPInterface.socket = socket

            BufferedOutputStream(socket.getOutputStream()).use { outputStream ->
                outStream = outputStream

                BufferedInputStream(socket.getInputStream()).use { inputStream ->
                    super.connect()

                    var timeoutCount = 0
                    while (timeoutCount < 180) try { // close after 90s of inactivity
                        val c = inputStream.read()
                        if (c == -1) {
                            warn("Got EOF on TCP stream")
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
            onDeviceDisconnect(false)
        }
    }
}

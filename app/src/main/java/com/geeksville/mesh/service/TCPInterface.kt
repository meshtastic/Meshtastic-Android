package com.geeksville.mesh.service

import com.geeksville.android.Logging
import com.geeksville.util.Exceptions
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread


class TCPInterface(service: RadioInterfaceService, private val address: String) :
    StreamInterface(service) {

    companion object : Logging, InterfaceFactory('t') {
        override fun createInterface(
            service: RadioInterfaceService,
            rest: String
        ): IRadioInterface = TCPInterface(service, rest)

        init {
            registerFactory()
        }
    }

    var socket: Socket? = null
    lateinit var outStream: OutputStream
    lateinit var inStream: InputStream

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
            socket = null
            outStream.close()
            inStream.close()
            s.close()
        }
        super.onDeviceDisconnect(waitForStopped)
    }

    override fun connect() {
        //here you must put your computer's IP address.
        //here you must put your computer's IP address.

        // No need to keep a reference to this thread - it will exit when we close inStream
        thread(start = true, isDaemon = true, name = "TCP reader") {
            try {
                val a = InetAddress.getByName(address)
                debug("TCP connecting to $address")

                //create a socket to make the connection with the server
                val port = 4403
                val s = Socket(a, port)
                s.tcpNoDelay = true
                s.soTimeout = 500
                socket = s
                outStream = BufferedOutputStream(s.getOutputStream())
                inStream = s.getInputStream()

                // Note: we call the super method FROM OUR NEW THREAD
                super.connect()

                while (true) {
                    try {
                        val c = inStream.read()
                        if (c == -1) {
                            warn("Got EOF on TCP stream")
                            onDeviceDisconnect(false)
                            break
                        } else
                            readChar(c.toByte())
                    } catch (ex: SocketTimeoutException) {
                        // Ignore and start another read
                    }
                }
            } catch (ex: IOException) {
                errormsg("IOException in TCP reader: $ex") // FIXME, show message to user
                onDeviceDisconnect(false)
            } catch (ex: Throwable) {
                Exceptions.report(ex, "Exception in TCP reader")
                onDeviceDisconnect(false)
            }
            debug("Exiting TCP reader")
        }
    }
}
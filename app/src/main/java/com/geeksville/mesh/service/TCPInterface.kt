package com.geeksville.mesh.service

import java.io.*
import java.net.InetAddress
import java.net.Socket
import kotlin.concurrent.thread


class TCPInterface(service: RadioInterfaceService, private val address: String) :
    StreamInterface(service) {

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
        if(s != null) {
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
        val addr = InetAddress.getByName(address)

        debug("TCP connecting to $address")

        //create a socket to make the connection with the server

        //create a socket to make the connection with the server
        val port = 4403
        val s = Socket(addr, port)
        s.tcpNoDelay = true
        socket = s
        outStream = BufferedOutputStream(s.getOutputStream())
        inStream = BufferedInputStream(s.getInputStream())

        // No need to keep a reference to this thread - it will exit when we close inStream
        thread(start = true, isDaemon = true, name = "TCP reader") {
            try {
                while(true) {
                    val c = inStream.read()
                    if(c == -1)
                        break;
                    else
                        readChar(c.toByte())
                }
            }
            catch(ex: Throwable) {
                errormsg("Exception in TCP reader: $ex")
                onDeviceDisconnect(false)
            }
            debug("Exiting TCP reader")
        }
        super.connect()
    }
}
package com.geeksville.mesh.service

import com.geeksville.android.Logging


/**
 * An interface that assumes we are talking to a meshtastic device over some sort of stream connection (serial or TCP probably)
 */
abstract class StreamInterface(protected val service: RadioInterfaceService) :
    Logging,
    IRadioInterface {
    companion object : Logging {
        private const val START1 = 0x94.toByte()
        private const val START2 = 0xc3.toByte()
        private const val MAX_TO_FROM_RADIO_SIZE = 512
    }

    private val debugLineBuf = kotlin.text.StringBuilder()

    /** The index of the next byte we are hoping to receive */
    private var ptr = 0

    /** The two halves of our length */
    private var msb = 0
    private var lsb = 0
    private var packetLen = 0

    override fun close() {
        debug("Closing stream for good")
        onDeviceDisconnect(true)
    }

    /** Tell MeshService our device has gone away, but wait for it to come back
     *
     * @param waitForStopped if true we should wait for the manager to finish - must be false if called from inside the manager callbacks
     *  */
    protected open fun onDeviceDisconnect(waitForStopped: Boolean) {
        service.onDisconnect(isPermanent = true) // if USB device disconnects it is definitely permantently gone, not sleeping)
    }

    protected open fun connect() {
        // Before telling mesh service, send a few START1s to wake a sleeping device
        val wakeBytes = byteArrayOf(START1, START1, START1, START1)
        sendBytes(wakeBytes)

        // Now tell clients they can (finally use the api)
        service.onConnect()
    }

    abstract fun sendBytes(p: ByteArray)

    /// If subclasses need to flash at the end of a packet they can implement
    open fun flushBytes() {}

    override fun handleSendToRadio(p: ByteArray) {
        // This method is called from a continuation and it might show up late, so check for uart being null

        val header = ByteArray(4)
        header[0] = START1
        header[1] = START2
        header[2] = (p.size shr 8).toByte()
        header[3] = (p.size and 0xff).toByte()

        sendBytes(header)
        sendBytes(p)
        flushBytes()
    }


    /** Print device serial debug output somewhere */
    private fun debugOut(b: Byte) {
        when (val c = b.toChar()) {
            '\r' -> {
            } // ignore
            '\n' -> {
                debug("DeviceLog: $debugLineBuf")
                debugLineBuf.clear()
            }
            else ->
                debugLineBuf.append(c)
        }
    }

    private val rxPacket = ByteArray(MAX_TO_FROM_RADIO_SIZE)

    protected fun readChar(c: Byte) {
        // Assume we will be advancing our pointer
        var nextPtr = ptr + 1

        fun lostSync() {
            errormsg("Lost protocol sync")
            nextPtr = 0
        }

        /// Deliver our current packet and restart our reader
        fun deliverPacket() {
            val buf = rxPacket.copyOf(packetLen)
            service.handleFromRadio(buf)

            nextPtr = 0 // Start parsing the next packet
        }

        when (ptr) {
            0 -> // looking for START1
                if (c != START1) {
                    debugOut(c)
                    nextPtr = 0 // Restart from scratch
                }
            1 -> // Looking for START2
                if (c != START2)
                    lostSync() // Restart from scratch
            2 -> // Looking for MSB of our 16 bit length
                msb = c.toInt() and 0xff
            3 -> { // Looking for LSB of our 16 bit length
                lsb = c.toInt() and 0xff

                // We've read our header, do one big read for the packet itself
                packetLen = (msb shl 8) or lsb
                if (packetLen > MAX_TO_FROM_RADIO_SIZE)
                    lostSync()  // If packet len is too long, the bytes must have been corrupted, start looking for START1 again
                else if (packetLen == 0)
                    deliverPacket() // zero length packets are valid and should be delivered immediately (because there won't be a next byte of payload)
            }
            else -> {
                // We are looking at the packet bytes now
                rxPacket[ptr - 4] = c

                // Note: we have to check if ptr +1 is equal to packet length (for example, for a 1 byte packetlen, this code will be run with ptr of4
                if (ptr - 4 + 1 == packetLen) {
                    deliverPacket()
                }
            }
        }
        ptr = nextPtr
    }
}
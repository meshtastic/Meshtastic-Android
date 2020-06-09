package com.geeksville.mesh.service

import android.content.Context
import android.hardware.usb.UsbManager
import com.geeksville.android.Logging
import com.geeksville.util.ignoreException
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager


class SerialInterface(private val service: RadioInterfaceService, val address: String) : Logging,
    IRadioInterface, SerialInputOutputManager.Listener {
    companion object : Logging {
        private const val START1 = 0x94.toByte()
        private const val START2 = 0xc3.toByte()
        private const val MAX_TO_FROM_RADIO_SIZE = 512

        fun findDrivers(context: Context): List<UsbSerialDriver> {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            val devices = drivers.map { it.device }
            devices.forEach { d ->
                debug("Found serial port ${d.deviceName}")
            }
            return drivers
        }

        fun addressValid(context: Context, rest: String): Boolean {
            findSerial(context, rest)?.let { d ->
                val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                return manager.hasPermission(d.device)
            }
            return false
        }

        fun findSerial(context: Context, rest: String): UsbSerialDriver? {
            val drivers = findDrivers(context)

            return if (drivers.isEmpty())
                null
            else  // Open a connection to the first available driver.
                drivers[0] // FIXME, instead we should find by name
        }
    }

    private var uart: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    init {
        val manager = service.getSystemService(Context.USB_SERVICE) as UsbManager

        val device = findSerial(service, address)

        if (device != null) {
            info("Opening $device")
            val connection = manager.openDevice(device.device)
            if (connection == null) {
                // FIXME add UsbManager.requestPermission(device, ..) handling to activity
                TODO("Need permissions for port")
            } else {
                val port = device.ports[0] // Most devices have just one port (port 0)

                connection
                port.open(connection)
                port.setParameters(921600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                uart = port

                debug("Starting serial reader thread")
                val io = SerialInputOutputManager(port, this)
                io.readTimeout = 200 // To save battery we only timeout ever so often
                ioManager = io

                val thread = Thread(io)
                thread.isDaemon = true
                thread.priority = Thread.MAX_PRIORITY
                thread.name = "serial reader"
                thread.start() // No need to keep reference to thread around, we quit by asking the ioManager to quit

                // Now tell clients they can (finally use the api)
                service.broadcastConnectionChanged(true, isPermanent = false)
            }
        } else {
            errormsg("Can't find device")
        }
    }

    override fun handleSendToRadio(p: ByteArray) {
        // This method is called from a continuation and it might show up late, so check for uart being null

        val header = ByteArray(4)
        header[0] = START1
        header[1] = START2
        header[2] = (p.size shr 8).toByte()
        header[3] = (p.size and 0xff).toByte()
        ioManager?.apply {
            writeAsync(header)
            writeAsync(p)
        }
    }

    /** Print device serial debug output somewhere */
    private fun debugOut(c: Byte) {
        debug("Got c: ${c.toChar()}")
    }

    /** The index of the next byte we are hoping to receive */
    var ptr = 0

    /** The two halves of our length */
    var msb = 0
    var lsb = 0

    var packetLen = 0

    private val rxPacket = ByteArray(MAX_TO_FROM_RADIO_SIZE)

    private fun readChar(c: Byte) {
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

    override fun close() {
        debug("Closing serial port")
        ignoreException { ioManager?.let { it.stop() } }
        ioManager = null
        ignoreException {
            uart?.close() // This will cause the reader thread to exit
        }
        uart = null
    }

    /**
     * Called when [SerialInputOutputManager.run] aborts due to an error.
     */
    override fun onRunError(e: java.lang.Exception) {
        errormsg("Serial error: $e")
        // FIXME - try to reconnect to the device when it comes back

        service.onDisconnect(isPermanent = false)
    }

    /**
     * Called when new incoming data is available.
     */
    override fun onNewData(data: ByteArray) {
        data.forEach(::readChar)
    }
}
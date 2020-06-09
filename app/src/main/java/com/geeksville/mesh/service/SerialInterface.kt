package com.geeksville.mesh.service

import android.content.Context
import android.hardware.usb.UsbManager
import com.geeksville.android.Logging
import com.geeksville.util.ignoreException
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors


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

                port.open(connection)
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                uart = port

                debug("Starting serial reader thread")
                ioManager = SerialInputOutputManager(port, this)
                Executors.newSingleThreadExecutor().submit(ioManager);
            }
        } else {
            errormsg("Can't find device")
        }
    }

    override fun handleSendToRadio(p: ByteArray) {
        // This method is called from a continuation and it might show up late, so check for uart being null
        uart?.apply {
            val header = ByteArray(4)
            header[0] = START1
            header[1] = START2
            header[2] = (p.size shr 8).toByte()
            header[3] = (p.size and 0xff).toByte()
            write(header, 0)
            write(p, 0)
        }
    }

    /** Print device serial debug output somewhere */
    private fun debugOut(c: Byte) {
        debug("Got c: ${c.toChar()}")
    }

    private fun readerLoop() {
        try {
            val scratch = ByteArray(1)

            /** The index of the next byte we are hoping to receive */
            var ptr = 0

            /** The two halves of our length */
            var msb = 0
            var lsb = 0

            val timeout = 0 // (60 * 1000)

            while (uart != null) { // we run until our port gets closed
                uart?.apply {
                    val numRead = read(scratch, timeout)
                    if (numRead == 0)
                        errormsg("Read returned zero bytes")
                    else {
                        val c = scratch[0]

                        // Assume we will be advancing our pointer
                        var nextPtr = ptr + 1

                        when (ptr) {
                            0 -> // looking for START1
                                if (c != START1) {
                                    debugOut(c)
                                    nextPtr = 0 // Restart from scratch
                                }
                            1 -> // Looking for START2
                                if (c != START2)
                                    nextPtr = 0 // Restart from scratch
                            2 -> // Looking for MSB of our 16 bit length
                                msb = c.toInt() and 0xff
                            3 -> // Looking for LSB of our 16 bit length
                                lsb = c.toInt() and 0xff
                            else -> { // We've read our header, do one big read for the packet itself
                                val packetLen = (msb shl 8) or lsb

                                // If packet len is too long, the bytes must have been corrupted, start looking for START1 again
                                if (packetLen <= MAX_TO_FROM_RADIO_SIZE) {
                                    val buf = ByteArray(packetLen)
                                    val numRead = read(buf, timeout)
                                    if (numRead < packetLen)
                                        errormsg("Packet read was too short")
                                    else
                                        service.handleFromRadio(buf)
                                }
                                nextPtr = 0 // Start parsing the next packet
                            }
                        }
                        ptr = nextPtr
                    }
                }
            }
        } catch (ex: Exception) {
            errormsg("Terminating reader thread due to ${ex.message}", ex)
        }
    }

    override fun close() {
        debug("Closing serial port")
        ioManager?.let { it.stop() }
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
    }

    /**
     * Called when new incoming data is available.
     */
    override fun onNewData(data: ByteArray) {
        TODO("Not yet implemented")
    }
}
package com.geeksville.mesh.service

import android.content.Context
import android.hardware.usb.UsbManager
import com.geeksville.android.Logging
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlin.concurrent.thread


class SerialInterface(private val service: RadioInterfaceService, val address: String) : Logging,
    IRadioInterface {
    companion object : Logging {
        private const val START1 = 0x94.toByte()
        private const val START2 = 0xc3.toByte()
        private const val MAX_TO_FROM_RADIO_SIZE = 512

        fun findDrivers(context: Context): List<UsbSerialDriver> {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            val devices = drivers.map { it.device }
            devices.forEach { d ->
                debug("Found serial port $d")
            }
            return drivers
        }
    }

    private var uart: UsbSerialPort?
    private lateinit var reader: Thread

    init {
        val manager = service.getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = findDrivers(this)

        // Open a connection to the first available driver.
        val device = drivers[0].device

        info("Opening $device")
        val connection = manager.openDevice(device)
        if (connection == null) {
            // FIXME add UsbManager.requestPermission(device, ..) handling to activity
            TODO("Need permissions for port")
        } else {
            val port = drivers[0].ports[0] // Most devices have just one port (port 0)

            port.open(connection)
            port.setParameters(921600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            uart = port

            debug("Starting serial reader thread")
            // FIXME, start reading thread
            reader =
                thread(start = true, isDaemon = true, name = "serial reader", block = ::readerLoop)
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

            while (uart != null) { // we run until our port gets closed
                uart?.apply {
                    read(scratch, 0)
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
                                read(buf, 0)
                                service.handleFromRadio(buf)
                            }
                            nextPtr = 0 // Start parsing the next packet
                        }
                    }
                    ptr = nextPtr
                }
            }
        } catch (ex: Exception) {
            errormsg("Terminating reader thread due to ${ex.message}", ex)
        }
    }

    override fun close() {
        debug("Closing serial port")
        uart?.close() // This will cause the reader thread to exit
        uart = null
    }
}
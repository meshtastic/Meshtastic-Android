package com.geeksville.mesh.service

import android.content.Context
import android.hardware.usb.UsbManager
import com.geeksville.android.Logging
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber


class SerialInterface(private val service: RadioInterfaceService, val address: String) : Logging,
    IRadioInterface {
    companion object {
        private const val START1 = 0x94.toByte()
        private const val START2 = 0xc3.toByte()
        private const val MAX_TO_FROM_RADIO_SIZE = 512
    }

    private var uart: UsbSerialPort? = null

    private val manager: UsbManager by lazy {
        service.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    init {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)

        // Open a connection to the first available driver.
        // Open a connection to the first available driver.
        val driver: UsbSerialDriver = drivers[0]
        val connection = manager.openDevice(driver.device)
        if (connection == null) {
            // FIXME add UsbManager.requestPermission(driver.getDevice(), ..) handling to activity
            TODO()
        } else {
            val port = driver.ports[0] // Most devices have just one port (port 0)

            port.open(connection)
            port.setParameters(921600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            uart = port

            // FIXME, start reading thread
        }
    }

    override fun handleSendToRadio(p: ByteArray) {
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

    }

    private fun readerLoop() {
        try {
            val scratch = ByteArray(1)

            /** The index of the next byte we are hoping to receive */
            var ptr = 0

            /** The two halves of our length */
            var msb = 0
            var lsb = 0

            while (true) { // FIXME wait for someone to ask us to exit, and catch continuation exception
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
        uart?.close()
        uart = null
    }
}